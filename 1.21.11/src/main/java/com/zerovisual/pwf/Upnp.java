package com.zerovisual.pwf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the home router to forward a UDP port to us (UPnP IGD), like games and torrent clients do.
 * With a forwarded port the friend reaches us directly, whatever router they have.
 * Only works when the router has the real internet address; behind provider NAT (CGNAT) the
 * router's own "external" address is private and the forward does not help.
 */
public final class Upnp {
	private static final String[] SERVICES = {
		"urn:schemas-upnp-org:service:WANIPConnection:2",
		"urn:schemas-upnp-org:service:WANIPConnection:1",
		"urn:schemas-upnp-org:service:WANPPPConnection:1",
	};

	private final String controlUrl;
	private final String service;
	private final String localIp;
	private int mappedPort = -1;

	private Upnp(String controlUrl, String service, String localIp) {
		this.controlUrl = controlUrl;
		this.service = service;
		this.localIp = localIp;
	}

	/** Finds the router, or null when there is none answering within about two seconds. */
	public static Upnp discover() {
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setSoTimeout(700);
			String search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n"
				+ "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
			byte[] data = search.getBytes(StandardCharsets.US_ASCII);
			InetSocketAddress group = new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900);
			byte[] buf = new byte[2048];
			long deadline = System.currentTimeMillis() + 2100;
			int sent = 0;
			while (System.currentTimeMillis() < deadline) {
				if (sent < 3) {
					socket.send(new DatagramPacket(data, data.length, group));
					sent++;
				}
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				try {
					socket.receive(p);
				} catch (SocketTimeoutException e) {
					continue;
				}
				String reply = new String(buf, 0, p.getLength(), StandardCharsets.US_ASCII);
				Matcher m = Pattern.compile("(?im)^location:\\s*(\\S+)").matcher(reply);
				if (!m.find()) continue;
				Upnp found = fromDescription(m.group(1));
				if (found != null) return found;
			}
		} catch (IOException ignored) {
		}
		return null;
	}

	private static Upnp fromDescription(String location) {
		try {
			URL url = URI.create(location).toURL();
			String xml = http(url, null, null);
			for (String service : SERVICES) {
				int at = xml.indexOf(service);
				if (at < 0) continue;
				Matcher m = Pattern.compile("<controlURL>\\s*([^<]+?)\\s*</controlURL>").matcher(xml);
				if (!m.find(at)) continue;
				String control = m.group(1);
				String full = control.startsWith("http") ? control : URI.create(location).resolve(control).toString();
				// our address as the router sees it
				String localIp;
				try (Socket probe = new Socket()) {
					probe.connect(new InetSocketAddress(url.getHost(), url.getPort() > 0 ? url.getPort() : 80), 1500);
					localIp = probe.getLocalAddress().getHostAddress();
				}
				return new Upnp(full, service, localIp);
			}
		} catch (IOException | IllegalArgumentException ignored) {
		}
		return null;
	}

	/** The router's internet address, or null. A private or 100.64.x.x address means provider NAT (CGNAT). */
	public String externalIp() {
		try {
			String reply = soap("GetExternalIPAddress", "");
			Matcher m = Pattern.compile("<NewExternalIPAddress>\\s*([^<]*?)\\s*</NewExternalIPAddress>").matcher(reply);
			return m.find() && !m.group(1).isEmpty() ? m.group(1) : null;
		} catch (IOException e) {
			return null;
		}
	}

	/** Forwards UDP {@code port} on the router to the same port here, for one hour. */
	public boolean forward(int port) {
		try {
			soap("AddPortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port + "</NewExternalPort>"
				+ "<NewProtocol>UDP</NewProtocol><NewInternalPort>" + port + "</NewInternalPort>"
				+ "<NewInternalClient>" + localIp + "</NewInternalClient><NewEnabled>1</NewEnabled>"
				+ "<NewPortMappingDescription>ZeroVisual Play with friends</NewPortMappingDescription><NewLeaseDuration>3600</NewLeaseDuration>");
			mappedPort = port;
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** Removes the forward again (closing the session). */
	public void remove() {
		if (mappedPort < 0) return;
		try {
			soap("DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + mappedPort + "</NewExternalPort><NewProtocol>UDP</NewProtocol>");
		} catch (IOException ignored) {
		}
		mappedPort = -1;
	}

	/** True for addresses that cannot be the real internet address of a home router. */
	public static boolean isPrivate(String ip) {
		try {
			InetAddress a = InetAddress.getByName(ip);
			byte[] b = a.getAddress();
			boolean cgnat = b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
			return a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || cgnat;
		} catch (IOException e) {
			return true;
		}
	}

	private String soap(String action, String args) throws IOException {
		String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
			+ "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action + " xmlns:u=\"" + service + "\">"
			+ args + "</u:" + action + "></s:Body></s:Envelope>";
		return http(URI.create(controlUrl).toURL(), body, "\"" + service + "#" + action + "\"");
	}

	private static String http(URL url, String body, String soapAction) throws IOException {
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setConnectTimeout(2000);
		c.setReadTimeout(3000);
		if (body != null) {
			c.setRequestMethod("POST");
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
			c.setRequestProperty("SOAPAction", soapAction);
			try (OutputStream out = c.getOutputStream()) {
				out.write(body.getBytes(StandardCharsets.UTF_8));
			}
		}
		int code = c.getResponseCode();
		try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			if (in != null) in.transferTo(bytes);
			if (code >= 400) throw new IOException("HTTP " + code);
			return bytes.toString(StandardCharsets.UTF_8);
		} finally {
			c.disconnect();
		}
	}
}
