cmake_minimum_required(VERSION 3.22.1)
project(nexora_core C)

add_library(nexora SHARED core.c)
if(NOT DEFINED NEXORA_DEBUG_BUILD)
    set(NEXORA_DEBUG_BUILD 0)
endif()
target_compile_definitions(nexora PRIVATE NEXORA_DEBUG_BUILD=${NEXORA_DEBUG_BUILD})
target_compile_options(nexora PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -fstack-protector-strong
)
target_link_options(nexora PRIVATE -Wl,--gc-sections)

find_library(log-lib log)
target_link_libraries(nexora ${log-lib})
