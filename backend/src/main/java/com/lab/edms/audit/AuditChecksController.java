package com.lab.edms.audit;

import com.lab.edms.audit.dto.AuditCheckpointDto;
import com.lab.edms.audit.dto.VerifyRequest;
import com.lab.edms.audit.dto.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs/checkpoints")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_AUDITOR')")
public class AuditChecksController {

    private final AnchorRepository anchorRepo;
    private final AuditChainVerifier verifier;

    public AuditChecksController(AnchorRepository anchorRepo, AuditChainVerifier verifier) {
        this.anchorRepo = anchorRepo;
        this.verifier = verifier;
    }

    @GetMapping
    public List<AuditCheckpointDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate fromOrEpoch = (from != null) ? from : LocalDate.of(1970, 1, 1);
        LocalDate toOrFuture  = (to   != null) ? to   : LocalDate.of(9999, 12, 31);
        return anchorRepo.findByRange(fromOrEpoch, toOrFuture)
                .stream().map(AuditCheckpointDto::from).toList();
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "to_date must not be before from_date");
        }
        return verifier.verify(req.fromDate(), req.toDate());
    }
}
