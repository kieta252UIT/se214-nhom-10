package com.realestates.backend.moderation.dto;

import java.util.List;

public record HangChoResponse(List<MucHangCho> items, String nextCursor) {
}
