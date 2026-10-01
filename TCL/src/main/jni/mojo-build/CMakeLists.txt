# Drop-in CMake for the 26.3 branch only.
# Point TCL/build.gradle.kts externalNativeBuild.cmake.path at this file
# on the 26.3-mojo branch. Do not switch 26.2 builds to this file.
cmake_minimum_required(VERSION 3.22.1)

add_subdirectory(mojoexec)

set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)
add_subdirectory(glfw)

set(SDL_SHARED       ON  CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON  CACHE BOOL "" FORCE)
set(SDL_AUDIO        ON  CACHE BOOL "" FORCE)
set(SDL_CAMERA       OFF CACHE BOOL "" FORCE)
set(SDL_TESTS        OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES     OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)
add_subdirectory(sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)
