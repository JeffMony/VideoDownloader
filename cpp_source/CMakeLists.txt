cmake_minimum_required(VERSION 3.9) #最低版本

project(video_downloader VERSION 1.0) #项目名称及自定义版本号

file(GLOB 
     video_downloader_source 
     main.cpp
)

add_executable(video_downloader ${video_downloader_source}) #添加生成可执行文件
