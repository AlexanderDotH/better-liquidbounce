/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import net.ccbluex.liquidbounce.event.EnvironmentEvent;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.ccbluex.liquidbounce.render.engine.type.Vec3f;
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup;
import net.ccbluex.liquidbounce.render.utils.VertexList;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/** Binary facade retained for callers compiled against the original Kotlin file. */
@NullMarked
public final class RenderShortcutsKt {
    public static final boolean HAS_AMD_VEGA_APU = WorldRenderEnvironmentKt.getHAS_AMD_VEGA_APU();
    public static final AABB FULL_BOX = WorldRenderEnvironmentKt.getFULL_BOX();
    public static final AABB EMPTY_BOX = WorldRenderEnvironmentKt.getEMPTY_BOX();

    private RenderShortcutsKt() { }

    public static <E> void renderEnvironment(EnvironmentEvent<E> event, Function1<? super E, Unit> draw) { WorldRenderEnvironmentKt.renderEnvironment(event, draw); }
    public static void withPositionRelativeToCamera(WorldRenderEnvironment environment, Function1<? super WorldRenderEnvironment, Unit> draw) { WorldRenderEnvironmentKt.withPositionRelativeToCamera(environment, draw); }
    public static void withPositionRelativeToCamera(WorldRenderEnvironment environment, double x, double y, double z, Function1<? super WorldRenderEnvironment, Unit> draw) { WorldRenderEnvironmentKt.withPositionRelativeToCamera(environment, x, y, z, draw); }
    public static void withPositionRelativeToCamera(WorldRenderEnvironment environment, Vec3 position, Function1<? super WorldRenderEnvironment, Unit> draw) { WorldRenderEnvironmentKt.withPositionRelativeToCamera(environment, position, draw); }
    public static void withPositionRelativeToCamera(WorldRenderEnvironment environment, Vec3i position, Function1<? super WorldRenderEnvironment, Unit> draw) { WorldRenderEnvironmentKt.withPositionRelativeToCamera(environment, position, draw); }
    public static boolean drawGenericBlockESP(RenderTarget target, CachedMeshStorage storage, RenderPipeline pipeline, DistanceFadeUniformValueGroup fade, Function0<GpuBufferSlice> transforms) { return WorldRenderEnvironmentKt.drawGenericBlockESP(target, storage, pipeline, fade, transforms); }
    public static boolean drawGenericBlockESP$default(RenderTarget target, CachedMeshStorage storage, RenderPipeline pipeline, DistanceFadeUniformValueGroup fade, Function0<GpuBufferSlice> transforms, int mask, @Nullable Object ignored) { return drawGenericBlockESP(target, storage, pipeline, fade, (mask & 8) != 0 ? RenderPassExtensionsKt::getDynamicTransformsUniform : transforms); }

    public static void drawCustomMeshTextured(WorldRenderEnvironment environment, AbstractTexture texture, RenderPipeline pipeline, Map<String, GpuBufferSlice> uniforms, Function2<? super VertexConsumer, ? super PoseStack.Pose, Unit> drawer) { WorldMeshDrawingKt.drawCustomMeshTextured(environment, texture, pipeline, uniforms, drawer); }
    public static void drawCustomMeshTextured$default(WorldRenderEnvironment environment, AbstractTexture texture, RenderPipeline pipeline, Map<String, GpuBufferSlice> uniforms, Function2<? super VertexConsumer, ? super PoseStack.Pose, Unit> drawer, int mask, @Nullable Object ignored) { drawCustomMeshTextured(environment, texture, (mask & 2) != 0 ? ClientRenderPipelines.INSTANCE.texQuads(true) : pipeline, (mask & 4) != 0 ? Collections.emptyMap() : uniforms, drawer); }
    public static void drawCustomMesh(WorldRenderEnvironment environment, RenderPipeline pipeline, Map<String, ? extends AbstractTexture> textures, Map<String, GpuBufferSlice> uniforms, Function2<? super VertexConsumer, ? super PoseStack.Pose, Unit> drawer) { WorldMeshDrawingKt.drawCustomMesh(environment, pipeline, textures, uniforms, drawer); }
    public static void drawCustomMesh$default(WorldRenderEnvironment environment, RenderPipeline pipeline, Map<String, ? extends AbstractTexture> textures, Map<String, GpuBufferSlice> uniforms, Function2<? super VertexConsumer, ? super PoseStack.Pose, Unit> drawer, int mask, @Nullable Object ignored) { drawCustomMesh(environment, pipeline, (mask & 2) != 0 ? Collections.emptyMap() : textures, (mask & 4) != 0 ? Collections.emptyMap() : uniforms, drawer); }
    public static void drawLine(WorldRenderEnvironment environment, Vec3f p1, Vec3f p2, int argb) { WorldMeshDrawingKt.drawLine(environment, p1, p2, argb); }
    public static void drawLine(WorldRenderEnvironment environment, Vec3 p1, Vec3 p2, int argb) { WorldLineDrawingKt.drawLine(environment, p1, p2, argb); }
    public static void drawLinesWithWidth(WorldRenderEnvironment environment, int argb, float width, Vec3f... positions) { WorldLineDrawingKt.drawLinesWithWidth(environment, argb, width, positions); }
    public static void drawLinesWithWidth(WorldRenderEnvironment environment, int argb, float width, VertexList positions) { WorldLineDrawingKt.drawLinesWithWidth(environment, argb, width, positions); }
    public static void drawLines(WorldRenderEnvironment environment, int argb, Vec3f... positions) { WorldLineDrawingKt.drawLines(environment, argb, positions); }
    public static void drawLines(WorldRenderEnvironment environment, int argb, VertexList positions) { WorldLineDrawingKt.drawLines(environment, argb, positions); }
    public static void drawLineStrip(WorldRenderEnvironment environment, int argb, Vec3f... positions) { WorldLineDrawingKt.drawLineStrip(environment, argb, positions); }
    public static void drawLineStrip(WorldRenderEnvironment environment, int argb, VertexList positions) { WorldLineDrawingKt.drawLineStrip(environment, argb, positions); }

    public static void drawTexQuad(WorldRenderEnvironment environment, AbstractTexture texture, int argb) { WorldTextureDrawingKt.drawTexQuad(environment, texture, argb); }
    public static void drawSquareTexture(WorldRenderEnvironment environment, AbstractTexture texture, float size, int argb, AnchorPoint anchor, boolean noDepthTest) { WorldTextureDrawingKt.drawSquareTexture(environment, texture, size, argb, anchor, noDepthTest); }
    public static void drawSquareTexture$default(WorldRenderEnvironment environment, AbstractTexture texture, float size, int argb, AnchorPoint anchor, boolean noDepthTest, int mask, @Nullable Object ignored) { drawSquareTexture(environment, texture, size, argb, (mask & 8) != 0 ? AnchorPoint.TOP_LEFT : anchor, (mask & 16) != 0 ? false : noDepthTest); }
    public static void drawSquareTextureGradient(WorldRenderEnvironment environment, AbstractTexture texture, float outerRadius, float innerRadius, Color4b outerColor, Color4b innerColor, AnchorPoint anchor, int subdivisions, float startOffset, boolean noDepthTest) { WorldTextureDrawingKt.drawSquareTextureGradient(environment, texture, outerRadius, innerRadius, outerColor, innerColor, anchor, subdivisions, startOffset, noDepthTest); }
    public static void drawSquareTextureGradient$default(WorldRenderEnvironment environment, AbstractTexture texture, float outerRadius, float innerRadius, Color4b outerColor, Color4b innerColor, AnchorPoint anchor, int subdivisions, float startOffset, boolean noDepthTest, int mask, @Nullable Object ignored) { drawSquareTextureGradient(environment, texture, outerRadius, innerRadius, outerColor, innerColor, (mask & 32) != 0 ? AnchorPoint.TOP_LEFT : anchor, (mask & 64) != 0 ? 16 : subdivisions, (mask & 128) != 0 ? 0.5f : startOffset, (mask & 256) != 0 || noDepthTest); }

    public static void drawTriangle(WorldRenderEnvironment environment, Vec3f p1, Vec3f p2, Vec3f p3, int argb, boolean noDepthTest) { WorldShapeDrawingKt.drawTriangle(environment, p1, p2, p3, argb, noDepthTest); }
    public static void drawTriangle$default(WorldRenderEnvironment environment, Vec3f p1, Vec3f p2, Vec3f p3, int argb, boolean noDepthTest, int mask, @Nullable Object ignored) { drawTriangle(environment, p1, p2, p3, argb, (mask & 16) != 0 || noDepthTest); }
    public static void drawTriangle(WorldRenderEnvironment environment, Vec3f p1, Vec3f p2, Vec3f p3, int argb) { drawTriangle(environment, p1, p2, p3, argb, true); }
    public static void drawBox(WorldRenderEnvironment environment, AABB box, @Nullable Color4b face, @Nullable Color4b outline, int faceVertices, int outlineVertices, boolean noDepthTest) { WorldShapeDrawingKt.drawBox(environment, box, face, outline, faceVertices, outlineVertices, noDepthTest); }
    public static void drawBox$default(WorldRenderEnvironment environment, AABB box, @Nullable Color4b face, @Nullable Color4b outline, int faceVertices, int outlineVertices, boolean noDepthTest, int mask, @Nullable Object ignored) { drawBox(environment, box, (mask & 2) != 0 ? Color4b.TRANSPARENT : face, (mask & 4) != 0 ? Color4b.TRANSPARENT : outline, (mask & 8) != 0 ? -1 : faceVertices, (mask & 16) != 0 ? -1 : outlineVertices, (mask & 32) != 0 || noDepthTest); }
    public static void drawShape(WorldRenderEnvironment environment, VoxelShape shape, @Nullable Color4b face, @Nullable Color4b outline) { WorldShapeDrawingKt.drawShape(environment, shape, face, outline); }
    public static void drawShape$default(WorldRenderEnvironment environment, VoxelShape shape, @Nullable Color4b face, @Nullable Color4b outline, int mask, @Nullable Object ignored) { drawShape(environment, shape, (mask & 2) != 0 ? Color4b.TRANSPARENT : face, (mask & 4) != 0 ? Color4b.TRANSPARENT : outline); }
    public static void drawShapeSide(WorldRenderEnvironment environment, VoxelShape shape, Direction side, Vec3 hit, @Nullable Color4b face, @Nullable Color4b outline) { WorldShapeDrawingKt.drawShapeSide(environment, shape, side, hit, face, outline); }
    public static void drawShapeSide$default(WorldRenderEnvironment environment, VoxelShape shape, Direction side, Vec3 hit, @Nullable Color4b face, @Nullable Color4b outline, int mask, @Nullable Object ignored) { drawShapeSide(environment, shape, side, hit, (mask & 8) != 0 ? Color4b.TRANSPARENT : face, (mask & 16) != 0 ? Color4b.TRANSPARENT : outline); }
    public static void drawBoxSide(WorldRenderEnvironment environment, AABB box, Direction side, @Nullable Color4b face, @Nullable Color4b outline) { WorldShapeDrawingKt.drawBoxSide(environment, box, side, face, outline); }
    public static void drawBoxSide$default(WorldRenderEnvironment environment, AABB box, Direction side, @Nullable Color4b face, @Nullable Color4b outline, int mask, @Nullable Object ignored) { drawBoxSide(environment, box, side, (mask & 4) != 0 ? Color4b.TRANSPARENT : face, (mask & 8) != 0 ? Color4b.TRANSPARENT : outline); }
    public static void drawBoxSides(WorldRenderEnvironment environment, AABB box, Iterable<? extends Direction> sides, @Nullable Color4b face, @Nullable Color4b outline) { WorldShapeDrawingKt.drawBoxSides(environment, box, sides, face, outline); }
    public static void drawBoxSides$default(WorldRenderEnvironment environment, AABB box, Iterable<? extends Direction> sides, @Nullable Color4b face, @Nullable Color4b outline, int mask, @Nullable Object ignored) { drawBoxSides(environment, box, sides, (mask & 4) != 0 ? Color4b.TRANSPARENT : face, (mask & 8) != 0 ? Color4b.TRANSPARENT : outline); }
    public static void drawPlane(WorldRenderEnvironment environment, float sizeX, float sizeZ, @Nullable Color4b fill, @Nullable Color4b outline, boolean noDepthTest) { WorldShapeDrawingKt.drawPlane(environment, sizeX, sizeZ, fill, outline, noDepthTest); }
    public static void drawPlane$default(WorldRenderEnvironment environment, float sizeX, float sizeZ, @Nullable Color4b fill, @Nullable Color4b outline, boolean noDepthTest, int mask, @Nullable Object ignored) { drawPlane(environment, sizeX, sizeZ, (mask & 4) != 0 ? Color4b.TRANSPARENT : fill, (mask & 8) != 0 ? Color4b.TRANSPARENT : outline, (mask & 16) != 0 || noDepthTest); }

    public static void drawGradientCircle(WorldRenderEnvironment environment, float outerRadius, float innerRadius, Color4b outerColor, Color4b innerColor, Vector3fc innerOffset, boolean noDepthTest) { WorldCircleDrawingKt.drawGradientCircle(environment, outerRadius, innerRadius, outerColor, innerColor, innerOffset, noDepthTest); }
    public static void drawGradientCircle$default(WorldRenderEnvironment environment, float outerRadius, float innerRadius, Color4b outerColor, Color4b innerColor, Vector3fc innerOffset, boolean noDepthTest, int mask, @Nullable Object ignored) { drawGradientCircle(environment, outerRadius, innerRadius, outerColor, innerColor, (mask & 16) != 0 ? new Vector3f() : innerOffset, (mask & 32) != 0 || noDepthTest); }
    public static void drawCircle(WorldRenderEnvironment environment, float radius, Color4b color) { WorldCircleDrawingKt.drawCircle(environment, radius, color); }
    public static void drawCircleOutline(WorldRenderEnvironment environment, float radius, Color4b color, boolean noDepthTest) { WorldCircleDrawingKt.drawCircleOutline(environment, radius, color, noDepthTest); }
    public static void drawCircleOutline$default(WorldRenderEnvironment environment, float radius, Color4b color, boolean noDepthTest, int mask, @Nullable Object ignored) { drawCircleOutline(environment, radius, color, (mask & 4) != 0 || noDepthTest); }
    public static void drawCircleOutline(WorldRenderEnvironment environment, float radius, Color4b color) { drawCircleOutline(environment, radius, color, true); }
    public static void drawGradientSides(WorldRenderEnvironment environment, double height, Color4b baseColor, Color4b topColor, AABB box) { WorldGradientDrawingKt.drawGradientSides(environment, height, baseColor, topColor, box); }
}
