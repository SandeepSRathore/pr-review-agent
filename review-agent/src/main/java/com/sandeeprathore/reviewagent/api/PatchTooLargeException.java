package com.sandeeprathore.reviewagent.api;

class PatchTooLargeException extends RuntimeException {

	PatchTooLargeException(int bytes, int limit) {
		super("Patch is %d bytes; the limit is %d (review.reviewer.max-patch-bytes)".formatted(bytes, limit));
	}

}
