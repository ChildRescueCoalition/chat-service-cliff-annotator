package org.mediacloud.cliff.servlet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchParseTextRequest {
    @NotEmpty
    private Collection<ParseTextRequest> requests;
    @NotNull
    private int numOfThreads = 1;
}
