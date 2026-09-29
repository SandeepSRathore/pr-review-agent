package com.sandeeprathore.reviewagent.orchestration;

import com.sandeeprathore.reviewagent.review.diff.PatchSet;

/** A change to review, independent of where it came from (GitHub, a local patch, an eval fixture). */
public record ReviewRequest(String title, String description, PatchSet patch) {

	public ReviewRequest {
		title = title == null || title.isBlank() ? "(untitled change)" : title;
		description = description == null ? "" : description;
	}

}
