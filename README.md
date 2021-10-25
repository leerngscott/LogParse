# LogParse

bin目录结构
```
build/install/
└── LogParse
    ├── bin
    │   ├── LogParse
    │   └── LogParse.bat
    └── lib
        ├── annotations-13.0.jar
        ├── gson-2.8.8.jar
        ├── kotlin-stdlib-1.5.31.jar
        ├── kotlin-stdlib-common-1.5.31.jar
        ├── kotlin-stdlib-jdk7-1.5.31.jar
        ├── kotlin-stdlib-jdk8-1.5.31.jar
        ├── kotlinx-coroutines-core-jvm-1.5.2-native-mt.jar
        └── LogParse-1.0-SNAPSHOT.jar
```

## 简单使用

> -d 指定日志目录，会自动搜索目录下以＂.log＂结尾的文件作为日志文件，搜索深度为1
```
./LogParse -d <dir1> -d <dir2> --all
```
> -f 指定日志文件,要求绝对路径或相对路径
```
./LogParse -f <file1> -f <file2> --all
```
    
> -d -f可以同时使用
> 输出默认在当前目录，也可以用-o指定
```
./LogParse -o <dir> --all  
```
    
> 默认过滤的日志是Info级别以上的日志，如果过滤的级别,可以使用-l参数
>> [V 0, D 1, I 2, W 3, E 4]  
```
./LogParse  -l 1 
```
 
## 结果的目录结构
```
log-parse-result/
├── detail
│   ├── pkg
│   │   └── com.fuxi.datareport
│   └── tag
│       └── FuxiMusic
├── pkg-summary  // 按tag进行汇总 
└── tag-summary  // 按进程名(一个应用可能存在多个进程，unknown表示未知进程)进行汇总 
```
  
