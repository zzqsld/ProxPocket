cmake_minimum_required(VERSION 3.22.1)
project(pm3bridge CXX)

set(PM3_CLIENT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/rrg-tmp/client)
set(PM3_PREBUILT ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpm3rrg_rdv4.so)

if(NOT EXISTS ${PM3_PREBUILT})
    message(FATAL_ERROR "缺少预编译库 ${PM3_PREBUILT}，请先运行 tools/build_pm3lib.sh")
endif()

add_library(pm3rrg_rdv4 SHARED IMPORTED GLOBAL)
set_target_properties(pm3rrg_rdv4 PROPERTIES IMPORTED_LOCATION ${PM3_PREBUILT})

add_library(pm3bridge SHARED pm3_jni.cpp)
target_include_directories(pm3bridge PRIVATE ${PM3_CLIENT_ROOT}/include)
target_link_libraries(pm3bridge PRIVATE pm3rrg_rdv4 log)
