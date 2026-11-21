#version 330 core

#define VS_MATERIAL_TERRAIN
#define VS_NATIVE_PIECE
#moj_import <vs_native_piece.glsl>
uniform int vs_PieceCutout;
#moj_import <vs_ship_materials.glsl>
#moj_import <vs_translucent_fragment.glsl>
