cmake_minimum_required(VERSION 3.22.1)
project(nexora_config C)

add_library(nexora_config SHARED native_config.c)

find_library(log-lib log)
target_link_libraries(nexora_config ${log-lib})
