package com.sandeeprathore.reviewagent.api;

import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sandeeprathore.reviewagent.orchestration.ReviewProperties;
import com.sandeeprathore.reviewagent.orchestration.ReviewReport;
import com.sandeeprathore.reviewagent.orchestration.ReviewRequest;
import com.sandeeprathore.reviewagent.orchestration.ReviewService;
import com.sandeeprathore.reviewagent.review.diff.UnifiedDiffParser;

/**
 * Reviews a patch posted directly, without GitHub. Useful before pushing and for demos:
 *
 * <pre>
 * git diff main | curl -s -H 'Content-Type: text/x-diff' --data-binary @- localhost:8080/api/reviews/local
 * </pre>
 */
@RestController
@RequestMapping("/api/reviews")
class LocalReviewController {

	private final ReviewService reviewService;

	private final UnifiedDiffParser parser;

	private final ReviewProperties properties;

	LocalReviewController(ReviewService reviewService, UnifiedDiffParser parser, ReviewProperties properties) {
		this.reviewService = reviewService;
		this.parser = parser;
		this.properties = properties;
	}

	@PostMapping(path = "/local", consumes = { MediaType.TEXT_PLAIN_VALUE, "text/x-diff", "text/x-patch" })
	ReviewReport reviewPatch(@RequestBody String patch, @RequestParam(required = false) String title) {
		return review(title, null, patch);
	}

	@PostMapping(path = "/local", consumes = MediaType.APPLICATION_JSON_VALUE)
	ReviewReport reviewJson(@Valid @RequestBody LocalReviewRequest request) {
		return review(request.title(), request.description(), request.patch());
	}

	private ReviewReport review(String title, String description, String patch) {
		int bytes = patch.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > properties.maxPatchBytes()) {
			throw new PatchTooLargeException(bytes, properties.maxPatchBytes());
		}
		return reviewService.review(new ReviewRequest(title, description, parser.parse(patch)));
	}

	record LocalReviewRequest(String title, String description, @NotBlank String patch) {
	}

}
