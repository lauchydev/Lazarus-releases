package dev.lauchy.lazarus.feature.staff;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * One drawn thing on the desk (a rounded box with its border, a shadow's rings, a scrolled edge's fade) as a single
 * GUI element of textured quads. Minecraft places each element it's given among the others by looking at what it
 * overlaps, so a box as one element instead of its seven parts costs a seventh as much to place, and every shape on
 * the same texture draws in one go.
 */
final class DeskShape implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup texture;
	private final Matrix3x2f pose;
	private final @Nullable ScreenRectangle scissor, bounds;
	/** Each quad as x0, y0, x1, y1, u0, v0, u1, v1. */
	private final float[] quads;
	private final int[] colours;
	private final int count;

	private DeskShape(RenderPipeline pipeline, TextureSetup texture, Matrix3x2f pose, @Nullable ScreenRectangle scissor, @Nullable ScreenRectangle bounds,
			float[] quads, int[] colours, int count) {
		this.pipeline = pipeline;
		this.texture = texture;
		this.pose = pose;
		this.scissor = scissor;
		this.bounds = bounds;
		this.quads = quads;
		this.colours = colours;
		this.count = count;
	}

	@Override
	public void buildVertices(VertexConsumer out) {
		for (int i = 0; i < count; i++) {
			int q = i * 8, c = colours[i];
			float x0 = quads[q], y0 = quads[q + 1], x1 = quads[q + 2], y1 = quads[q + 3];
			float u0 = quads[q + 4], v0 = quads[q + 5], u1 = quads[q + 6], v1 = quads[q + 7];
			out.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(c);
			out.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(c);
			out.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(c);
			out.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(c);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return texture;
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return scissor;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return bounds;
	}

	/** Collects a shape's quads, in screen pixels under the pose they're drawn with. */
	static final class Builder {
		private float[] quads = new float[8 * 16];
		private int[] colours = new int[16];
		private int count;
		private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

		boolean isEmpty() {
			return count == 0;
		}

		void add(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int colour) {
			if (count == colours.length) {
				quads = Arrays.copyOf(quads, quads.length * 2);
				colours = Arrays.copyOf(colours, colours.length * 2);
			}
			int q = count * 8;
			quads[q] = x0;
			quads[q + 1] = y0;
			quads[q + 2] = x1;
			quads[q + 3] = y1;
			quads[q + 4] = u0;
			quads[q + 5] = v0;
			quads[q + 6] = u1;
			quads[q + 7] = v1;
			colours[count++] = colour;
			minX = Math.min(minX, x0);
			minY = Math.min(minY, y0);
			maxX = Math.max(maxX, x1);
			maxY = Math.max(maxY, y1);
		}

		/** The shape, placed by where its quads reach (within the clip, or nowhere when clipped away). */
		DeskShape build(RenderPipeline pipeline, TextureSetup texture, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
			int x = (int) Math.floor(minX), y = (int) Math.floor(minY);
			ScreenRectangle b = new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x, (int) Math.ceil(maxY) - y).transformMaxBounds(pose);
			if (scissor != null) b = scissor.intersection(b);
			return new DeskShape(pipeline, texture, pose, scissor, b, quads, colours, count);
		}
	}
}
