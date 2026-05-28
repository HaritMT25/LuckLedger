package com.luckledger.api;

import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.generation.theme.ThemeSkinningService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only catalogue of the available themes. */
@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeSkinningService themes;

    public ThemeController(ThemeSkinningService themes) {
        this.themes = themes;
    }

    @GetMapping
    public List<ThemeSummary> list() {
        return themes.getAvailableThemes().stream()
                .map(t -> new ThemeSummary(t.themeId(), t.name(), t.symbolMap().size()))
                .toList();
    }

    @GetMapping("/{themeId}")
    public ThemeDetail get(@PathVariable String themeId) {
        ThemeRef theme = themes.getAvailableThemes().stream()
                .filter(t -> t.themeId().equals(themeId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("no theme with id " + themeId));
        ColorPalette p = theme.palette();
        return new ThemeDetail(
                theme.themeId(),
                theme.name(),
                theme.symbolMap().size(),
                new PaletteDto(p.primary(), p.secondary(), p.accent(), p.background(), p.text()));
    }

    public record ThemeSummary(String themeId, String name, int symbolCount) {}

    public record ThemeDetail(String themeId, String name, int symbolCount, PaletteDto palette) {}

    public record PaletteDto(
            String primary, String secondary, String accent, String background, String text) {}
}
