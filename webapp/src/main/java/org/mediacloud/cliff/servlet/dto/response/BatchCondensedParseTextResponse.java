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
public class BatchCondensedParseTextResponse {
    private Collection<ParseTextResponse> results;
    private Long actualMilliseconds;
    private Long sumOfMilliseconds;
}
