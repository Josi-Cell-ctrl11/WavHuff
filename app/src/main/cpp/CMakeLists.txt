# Construit deux bibliothèques partagées : le moteur LAME et notre passerelle JNI.
cmake_minimum_required(VERSION 3.22.1)
project(wavhuff C)
set(CMAKE_C_STANDARD 99)
set(LAME_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lame)
set(LAME_SOURCES VbrTag bitstream encoder fft gain_analysis id3tag lame newmdct presets psymodel quantize quantize_pvt reservoir set_get tables takehiro util vbrquantize version mpglib_interface)
set(LAME_FILES "")
foreach(name IN LISTS LAME_SOURCES)
    list(APPEND LAME_FILES ${LAME_DIR}/libmp3lame/${name}.c)
endforeach()
# Le moteur tiers est conservé séparément (licence LGPL dans lame/COPYING).
add_library(mp3lame SHARED ${LAME_FILES})
target_include_directories(mp3lame PUBLIC ${LAME_DIR}/include PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} ${LAME_DIR}/libmp3lame)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
target_link_libraries(mp3lame m)
# Kotlin charge libwavhuff.so, qui appelle les fonctions de libmp3lame.so.
add_library(wavhuff SHARED bridge.c)
target_link_libraries(wavhuff mp3lame)
