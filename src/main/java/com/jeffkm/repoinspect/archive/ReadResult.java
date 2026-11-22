package com.jeffkm.repoinspect.archive;

import java.util.List;

/** truncated = 상한에 걸려 앞부분만 읽었다. 결과 화면에 "일부만 분석"으로 표시한다. */
public record ReadResult(List<SourceFile> files, boolean truncated) {
}
