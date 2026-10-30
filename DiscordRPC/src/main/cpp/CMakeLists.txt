cmake_minimum_required(VERSION 3.22.1)
project(tcdiscordrpc LANGUAGES CXX)

# The Discord Social SDK ships as an AAR with a Prefab package. Only that one module is
# imported here; it is staged by the :DiscordRPC prepareDiscordSdk Gradle task.
if(NOT DISCORD_SDK_DIR)
    message(FATAL_ERROR "DISCORD_SDK_DIR must point at the prepareDiscordSdk output")
endif()
set(discord_module "${DISCORD_SDK_DIR}/prefab/modules/discord_partner_sdk")
if(NOT EXISTS "${discord_module}/include/discordpp.h")
    message(FATAL_ERROR "Discord Social SDK headers not found in ${discord_module}")
endif()

# The SDK's own library is static-STL linked and is packaged from the AAR by AGP, so it is
# imported here only to satisfy the linker.
add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION "${discord_module}/libs/android.${ANDROID_ABI}/libdiscord_partner_sdk.so"
    INTERFACE_INCLUDE_DIRECTORIES "${discord_module}/include")

add_library(tcdiscordrpc SHARED discord_rpc_bridge.cpp)
target_link_libraries(tcdiscordrpc discord_partner_sdk log)
set_target_properties(tcdiscordrpc PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED YES
    CXX_EXCEPTIONS ON)
