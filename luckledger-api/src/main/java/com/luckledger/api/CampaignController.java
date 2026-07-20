package com.luckledger.api;

import com.luckledger.api.CampaignService.CampaignAnalytics;
import com.luckledger.api.CampaignService.CampaignPreview;
import com.luckledger.api.CampaignService.CampaignSummary;
import com.luckledger.domain.orchestration.GameStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The master's campaign dashboard API. Every route requires {@code ROLE_MASTER}, enforced both by the
 * {@link SecurityConfig} URL rules ({@code /api/master/**}) and the method-security annotation here, so a
 * config regression cannot silently open it (matching {@link MasterController}).
 *
 * <p>The dashboard lets the operator design a campaign's economics ({@code POST /preview}), commit one
 * through the verified generation pipeline ({@code POST}), read how it is doing against its fixed design
 * ({@code GET /{id}/analytics}), and stop or resume selling it ({@code /retire}, {@code /activate}).
 * There is deliberately no endpoint to retune an existing campaign's RTP — that is impossible by
 * construction; the operator retires and creates a new campaign instead.
 */
@RestController
@RequestMapping("/api/master/campaigns")
@PreAuthorize("hasRole('MASTER')")
public class CampaignController {

    private final CampaignService campaigns;

    public CampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    /**
     * Lists every campaign with its lifecycle and headline sales.
     *
     * @return all campaigns
     */
    @GetMapping
    public List<CampaignSummary> list() {
        return campaigns.list();
    }

    /**
     * Previews a campaign's server-derived economics without creating anything.
     *
     * @param request the campaign to preview
     * @return the preview (designed RTP, winners, budget, win frequency, validity, errors)
     */
    @PostMapping("/preview")
    public CampaignPreview preview(@Valid @RequestBody CreateCampaignRequest request) {
        return campaigns.preview(request);
    }

    /**
     * Creates a campaign through the verified generation pipeline and stocks it in the chosen shops.
     *
     * @param request the campaign to create
     * @return {@code 201} with the created campaign's summary
     */
    @PostMapping
    public ResponseEntity<CampaignSummary> create(@Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaigns.create(request));
    }

    /**
     * The full analytics for one campaign, reconciling its design against the ledger.
     *
     * @param gameId the campaign id
     * @return the analytics
     */
    @GetMapping("/{gameId}/analytics")
    public CampaignAnalytics analytics(@PathVariable UUID gameId) {
        return campaigns.analytics(gameId);
    }

    /**
     * Retires a campaign: no more purchases, no restock. Sold tickets still reveal and pay.
     *
     * @param gameId the campaign id
     * @return the updated summary
     */
    @PostMapping("/{gameId}/retire")
    public CampaignSummary retire(@PathVariable UUID gameId) {
        return campaigns.setStatus(gameId, GameStatus.RETIRED);
    }

    /**
     * Reactivates a retired campaign, restoring purchasing.
     *
     * @param gameId the campaign id
     * @return the updated summary
     */
    @PostMapping("/{gameId}/activate")
    public CampaignSummary activate(@PathVariable UUID gameId) {
        return campaigns.setStatus(gameId, GameStatus.ACTIVE);
    }
}
