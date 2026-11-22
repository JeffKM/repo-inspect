package com.jeffkm.repoinspect.archive;

/** 저장소 안의 텍스트 파일 하나. path 는 저장소 루트 기준(예: src/index.js). */
public record SourceFile(String path, String content) {
}
