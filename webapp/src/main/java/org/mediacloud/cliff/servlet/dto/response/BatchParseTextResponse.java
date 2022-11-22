package org.mediacloud.cliff.servlet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchParseTextResponse {
    public Collection<ParseTextResponse> results;
    public Long actualMilliseconds;
    public Long sumOfMilliseconds;
}
