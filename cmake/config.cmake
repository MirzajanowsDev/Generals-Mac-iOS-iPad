add_library(core_config INTERFACE)

# GeneralsX @bugfix android-port 08/09/2026 Apply engine-scoped ASAN
# instrumentation (set in compilers.cmake). See the comment there for why the
# compile flag is scoped to core_config rather than added globally.
if(RTS_BUILD_OPTION_ASAN AND RTS_ASAN_COMPILE_FLAGS)
    target_compile_options(core_config INTERFACE ${RTS_ASAN_COMPILE_FLAGS})
endif()

include(${CMAKE_CURRENT_LIST_DIR}/config-build.cmake)
include(${CMAKE_CURRENT_LIST_DIR}/config-debug.cmake)
include(${CMAKE_CURRENT_LIST_DIR}/config-memory.cmake)
