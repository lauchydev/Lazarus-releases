package dev.lauchy.lazarus.feature.staff;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A run of text on the desk as a single GUI element: the glyphs of one font page, as Minecraft's own text drawing
 * makes them, but placed and sorted like a shape. Minecraft keeps its glyphs in the order the text was drawn, so each
 * run of a different font breaks the batch; as elements, every run on the same page in a layer draws in one go.
 */
final class DeskText implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup texture;
	private final Matrix3x2f pose;
	private final @Nullable ScreenRectangle scissor, bounds;
	private final List<TextRenderable> glyphs;

	private static final Map<GpuTextureView, TextureSetup> SETUPS = new LinkedHashMap<>();

	private DeskText(RenderPipeline pipeline, TextureSetup texture, Matrix3x2f pose, @Nullable ScreenRectangle scissor, @Nullable ScreenRectangle bounds,
			List<TextRenderable> glyphs) {
		this.pipeline = pipeline;
		this.texture = texture;
		this.pose = pose;
		this.scissor = scissor;
		this.bounds = bounds;
		this.glyphs = glyphs;
	}

	@Override
	public void buildVertices(VertexConsumer out) {
		Matrix4f m = new Matrix4f().mul(pose);
		for (TextRenderable g : glyphs) g.render(m, out, 15728880, true);
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

	/** Gathers prepared text, glyph by glyph, into one element per font page. */
	static final class Collector implements Font.GlyphVisitor {
		private final Map<GpuTextureView, List<TextRenderable>> pages = new LinkedHashMap<>();
		private float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;

		@Override
		public void acceptGlyph(TextRenderable.Styled glyph) {
			add(glyph);
		}

		@Override
		public void acceptEffect(TextRenderable effect) {
			add(effect);
		}

		private void add(TextRenderable g) {
			pages.computeIfAbsent(g.textureView(), k -> new ArrayList<>()).add(g);
			left = Math.min(left, g.left());
			top = Math.min(top, g.top());
			right = Math.max(right, g.right());
			bottom = Math.max(bottom, g.bottom());
		}

		boolean isEmpty() {
			return pages.isEmpty();
		}

		/** The elements, one per page, placed by where the glyphs reach. */
		List<DeskText> build(Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
			int x = (int) Math.floor(left), y = (int) Math.floor(top);
			ScreenRectangle b = new ScreenRectangle(x, y, (int) Math.ceil(right) - x, (int) Math.ceil(bottom) - y).transformMaxBounds(pose);
			if (scissor != null) b = scissor.intersection(b);
			List<DeskText> out = new ArrayList<>(pages.size());
			for (var e : pages.entrySet()) {
				TextureSetup setup = SETUPS.computeIfAbsent(e.getKey(),
						v -> TextureSetup.singleTextureWithLightmap(v, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
				out.add(new DeskText(e.getValue().getFirst().guiPipeline(), setup, pose, scissor, b, e.getValue()));
			}
			if (SETUPS.size() > 64) SETUPS.clear();
			return out;
		}
	}
}
