package com.zerovisual.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Ed25519 signature verification (RFC 8032), because Java 8 has none. Only verifying, with plain affine
 * BigInteger arithmetic: slow, but it runs once at startup on a background thread.
 */
final class Ed25519 {
	private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
	private static final BigInteger D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
	private static final BigInteger L = BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));
	private static final BigInteger SQRT_M1 = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P);
	private static final BigInteger[] BASE;

	static {
		BigInteger y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);
		BASE = new BigInteger[]{recoverX(y, 0), y};
	}

	private Ed25519() {
	}

	/** @param publicKey the 32 raw key bytes, @param signature the 64 signature bytes */
	static boolean verify(byte[] publicKey, byte[] message, byte[] signature) throws NoSuchAlgorithmException {
		if (publicKey.length != 32 || signature.length != 64) return false;
		BigInteger[] a = decode(publicKey);
		byte[] rBytes = Arrays.copyOfRange(signature, 0, 32);
		BigInteger[] r = decode(rBytes);
		BigInteger s = littleEndian(Arrays.copyOfRange(signature, 32, 64));
		if (a == null || r == null || s.compareTo(L) >= 0) return false;
		MessageDigest sha = MessageDigest.getInstance("SHA-512");
		sha.update(rBytes);
		sha.update(publicKey);
		sha.update(message);
		BigInteger k = littleEndian(sha.digest()).mod(L);
		BigInteger[] left = multiply(BASE, s);
		BigInteger[] right = add(r, multiply(a, k));
		return left[0].equals(right[0]) && left[1].equals(right[1]);
	}

	private static BigInteger littleEndian(byte[] bytes) {
		byte[] big = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++) big[i] = bytes[bytes.length - 1 - i];
		return new BigInteger(1, big);
	}

	/** A point from its 32-byte encoding: y with the sign of x in the top bit; null when it is not on the curve. */
	private static BigInteger[] decode(byte[] encoded) {
		byte[] copy = encoded.clone();
		int sign = (copy[31] >> 7) & 1;
		copy[31] &= 0x7F;
		BigInteger y = littleEndian(copy);
		if (y.compareTo(P) >= 0) return null;
		BigInteger x = recoverX(y, sign);
		return x == null ? null : new BigInteger[]{x, y};
	}

	private static BigInteger recoverX(BigInteger y, int sign) {
		BigInteger y2 = y.multiply(y).mod(P);
		BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
		BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
		// x = u v^3 (u v^7)^((p - 5) / 8)
		BigInteger v3 = v.modPow(BigInteger.valueOf(3), P);
		BigInteger v7 = v.modPow(BigInteger.valueOf(7), P);
		BigInteger x = u.multiply(v3).multiply(u.multiply(v7).mod(P).modPow(P.subtract(BigInteger.valueOf(5)).shiftRight(3), P)).mod(P);
		BigInteger vx2 = v.multiply(x).multiply(x).mod(P);
		if (!vx2.equals(u)) {
			if (vx2.equals(P.subtract(u).mod(P))) x = x.multiply(SQRT_M1).mod(P);
			else return null;
		}
		if (x.signum() == 0 && sign == 1) return null;
		if (x.testBit(0) != (sign == 1)) x = P.subtract(x);
		return x;
	}

	/** Addition on the twisted Edwards curve -x^2 + y^2 = 1 + d x^2 y^2. */
	private static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
		BigInteger x1 = a[0], y1 = a[1], x2 = b[0], y2 = b[1];
		BigInteger dxy = D.multiply(x1).multiply(x2).multiply(y1).multiply(y2).mod(P);
		BigInteger x3 = x1.multiply(y2).add(x2.multiply(y1)).multiply(BigInteger.ONE.add(dxy).modInverse(P)).mod(P);
		BigInteger y3 = y1.multiply(y2).add(x1.multiply(x2)).multiply(BigInteger.ONE.subtract(dxy).mod(P).modInverse(P)).mod(P);
		return new BigInteger[]{x3, y3};
	}

	private static BigInteger[] multiply(BigInteger[] point, BigInteger scalar) {
		BigInteger[] result = {BigInteger.ZERO, BigInteger.ONE};
		BigInteger[] addend = point;
		for (int i = 0; i < scalar.bitLength(); i++) {
			if (scalar.testBit(i)) result = add(result, addend);
			addend = add(addend, addend);
		}
		return result;
	}
}
