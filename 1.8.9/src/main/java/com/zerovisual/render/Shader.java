package com.zerovisual.render;

import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** A GLSL program of the mod, loaded from assets/zerovisual/shaders/<name>.vsh and .fsh on first use. */
public final class Shader {
	private final String name;
	private final Map<String, Integer> uniforms = new HashMap<String, Integer>();
	private int program = -1;
	private boolean failed;

	public Shader(String name) {
		this.name = name;
	}

	/** Binds the program; false if it could not be built (the caller then draws a plain fallback). */
	public boolean bind() {
		if (failed) return false;
		if (program < 0 && !build()) {
			failed = true;
			return false;
		}
		GL20.glUseProgram(program);
		return true;
	}

	public void unbind() {
		GL20.glUseProgram(0);
	}

	public void set(String uniform, float... values) {
		int location = location(uniform);
		if (location < 0) return;
		switch (values.length) {
			case 1:
				GL20.glUniform1f(location, values[0]);
				break;
			case 2:
				GL20.glUniform2f(location, values[0], values[1]);
				break;
			case 3:
				GL20.glUniform3f(location, values[0], values[1], values[2]);
				break;
			default:
				GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
		}
	}

	public void setInt(String uniform, int value) {
		int location = location(uniform);
		if (location >= 0) GL20.glUniform1i(location, value);
	}

	private int location(String uniform) {
		Integer cached = uniforms.get(uniform);
		if (cached == null) {
			cached = GL20.glGetUniformLocation(program, uniform);
			uniforms.put(uniform, cached);
		}
		return cached;
	}

	private boolean build() {
		try {
			int vertex = compile(GL20.GL_VERTEX_SHADER, source(".vsh"));
			int fragment = compile(GL20.GL_FRAGMENT_SHADER, source(".fsh"));
			if (vertex < 0 || fragment < 0) return false;
			int p = GL20.glCreateProgram();
			GL20.glAttachShader(p, vertex);
			GL20.glAttachShader(p, fragment);
			GL20.glLinkProgram(p);
			if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
				ZeroVisualClient.LOGGER.error("Shader {} did not link: {}", name, GL20.glGetProgramInfoLog(p, 4096));
				return false;
			}
			program = p;
			return true;
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Shader {} could not be loaded", name, e);
			return false;
		}
	}

	private String source(String extension) throws Exception {
		ResourceLocation location = new ResourceLocation(ZeroVisualClient.MOD_ID, "shaders/" + name + extension);
		InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
		try {
			return IOUtils.toString(in, StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}

	private int compile(int type, String source) {
		int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			ZeroVisualClient.LOGGER.error("Shader {} did not compile: {}", name, GL20.glGetShaderInfoLog(shader, 4096));
			return -1;
		}
		return shader;
	}
}
