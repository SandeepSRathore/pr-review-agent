package com.sandeeprathore.reviewagent.review.finding;

import java.util.List;

public record ValidatedFindings(List<Finding> accepted, List<RejectedFinding> rejected) {

	public ValidatedFindings {
		accepted = List.copyOf(accepted);
		rejected = List.copyOf(rejected);
	}

}
