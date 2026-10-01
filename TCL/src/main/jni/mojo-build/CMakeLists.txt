cmake_minimum_required(VERSION 3.22.1)
project(mojo26)

# Sibling folders: ../mojoexec ../sdl ../glfw
# Do not replace Android.mk with this. This only builds Mojo's three libs.

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../mojoexec ${CMAKE_BINARY_DIR}/mojoexec)

set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../glfw ${CMAKE_BINARY_DIR}/glfw)

set(SDL_SHARED       ON  CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON  CACHE BOOL "" FORCE)
set(SDL_AUDIO        ON  CACHE BOOL "" FORCE)
set(SDL_CAMERA       OFF CACHE BOOL "" FORCE)
set(SDL_TESTS        OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES     OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../sdl ${CMAKE_BINARY_DIR}/sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)
