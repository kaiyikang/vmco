package com.reviewm.application.port.out;

import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;

import java.util.List;

public interface CodeContextPort {
    boolean supports(ChangedFile file);

    List<CodeContext> collect(ChangedFile file, List<String> lines);
}
