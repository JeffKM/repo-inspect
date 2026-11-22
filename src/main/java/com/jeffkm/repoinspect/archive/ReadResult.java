package com.jeffkm.repoinspect.archive;

import java.util.List;

/**
 * truncated = 상한에 걸려 앞부분만 읽었거나 크기 초과 파일을 건너뛰었다. 결과 화면에 "일부만 분석"으로 표시한다.
 * skippedFiles = 크기 초과나 바이너리로 건너뛴 파일 수.
 */
public record ReadResult(List<SourceFile> files, boolean truncated, int skippedFiles) {
}
