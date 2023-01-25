package org.mediacloud.cliff.servlet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchRawParseTextResponse {
    private Collection<RawParseTextResponse> results;
    private Long actualMilliseconds;
    private Long sumOfMilliseconds;
}
