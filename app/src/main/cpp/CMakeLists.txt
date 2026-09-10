cmake_minimum_required(VERSION 3.22.1)
project(agentterm-pty C)

add_library(pty SHARED pty.c)
target_compile_options(pty PRIVATE -Wno-unused-parameter -O2)
target_link_libraries(pty)