package org.mediacloud.cliff.servlet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchParseTextResponse {
    @NotEmpty
    public Collection<ParseTextResponse> results;
}
