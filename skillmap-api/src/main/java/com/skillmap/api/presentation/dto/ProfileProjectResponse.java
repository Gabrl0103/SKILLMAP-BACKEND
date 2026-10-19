package com.skillmap.api.presentation.dto;

import java.util.List;

public record ProfileProjectResponse(Long id, String name, String description, String url, List<String> tags) {
}
