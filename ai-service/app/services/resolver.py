"""Deterministic Resolution Engine in Python mirroring Java ResolutionService.

Arbitrates between Synthesizer answers and Critic assessments using deterministic rules:
1. Evidence Existence Evaluation (empty evidence or critic insufficient verdict -> INSUFFICIENT_EVIDENCE)
2. Citation & Grounding Validation (claims citing hallucinated or unverified chunk IDs are rejected)
3. Contradiction & Scope Differentiation (different scopes do not constitute genuine conflicts)
4. Explicit Supersession (newer effective version with explicit supersession link resolves older version)
5. Authority Precedence (higher authority rank resolves lower authority rank on same scope)
6. Conflict Preservation (equally authoritative, equally current sources on same scope -> CONFLICT, abstain)
"""

import logging
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set
from uuid import UUID

from app.schemas.critic import ContradictionFinding, CriticResponse, CriticVerdict, SourcePosition, StalenessFinding
from app.schemas.retrieval import RetrievedPassage
from app.schemas.synthesis import AtomicClaim

logger = logging.getLogger(__name__)

AUTHORITY_RANKS = {
    "REGULATION": 4,
    "STATUTE": 4,
    "OFFICIAL": 3,
    "POLICY": 3,
    "SPECIFICATION": 2,
    "STANDARD": 2,
    "INTERNAL": 2,
    "DRAFT": 1,
    "INFORMATIONAL": 1,
    "WORKING_DRAFT": 1,
}


@dataclass
class ResolutionResult:
    status: str  # ANSWERED, ANSWERED_WITH_QUALIFICATION, CONFLICT, INSUFFICIENT_EVIDENCE
    final_answer: Optional[str]
    explanation: str
    conflicting_positions: List[Dict[str, Any]] = field(default_factory=list)
    rejected_claims: List[Dict[str, Any]] = field(default_factory=list)
    accepted_claims: List[Dict[str, Any]] = field(default_factory=list)
    qualifications: List[str] = field(default_factory=list)
    evidence_references: List[Dict[str, Any]] = field(default_factory=list)


class DeterministicResolver:
    """Python implementation of TruthGuard's deterministic resolution engine."""

    def resolve(
        self,
        question: str,
        candidate_answer: Optional[str],
        claims: List[AtomicClaim],
        passages: List[RetrievedPassage],
        critic_res: CriticResponse,
    ) -> ResolutionResult:
        qualifications: List[str] = []
        conflicting_positions: List[Dict[str, Any]] = []

        # =========================================================================
        # PHASE 1: Evidence Existence Check
        # =========================================================================
        if not passages:
            return ResolutionResult(
                status="INSUFFICIENT_EVIDENCE",
                final_answer=None,
                explanation="Insufficient evidence: no relevant evidence passages were retrieved.",
            )

        if critic_res.verdict == CriticVerdict.INSUFFICIENT_EVIDENCE:
            return ResolutionResult(
                status="INSUFFICIENT_EVIDENCE",
                final_answer=None,
                explanation=f"Critic determined evidence is insufficient: {critic_res.explanation or 'insufficient facts in evidence set.'}",
            )

        # =========================================================================
        # PHASE 2: Citation & Grounding Validation
        # =========================================================================
        passage_by_id = {p.chunk_id: p for p in passages}
        valid_chunk_ids = set(passage_by_id.keys())

        if not candidate_answer or not candidate_answer.strip():
            return ResolutionResult(
                status="INSUFFICIENT_EVIDENCE",
                final_answer=None,
                explanation="Insufficient evidence: synthesizer produced no candidate answer.",
            )

        accepted_claims: List[Dict[str, Any]] = []
        rejected_claims: List[Dict[str, Any]] = []
        critic_unsupported_set = set(critic_res.unsupported_claim_ids or [])

        for claim in claims:
            cited = claim.cited_chunk_ids or []
            if not cited:
                rejected_claims.append({
                    "claim_id": claim.claim_id,
                    "text": claim.text,
                    "reason": "Claim lacks evidence citations",
                })
                continue

            invalid_ids = [str(cid) for cid in cited if cid not in valid_chunk_ids]
            if claim.invalid_chunk_ids:
                for inv in claim.invalid_chunk_ids:
                    if inv not in invalid_ids:
                        invalid_ids.append(inv)

            if invalid_ids:
                rejected_claims.append({
                    "claim_id": claim.claim_id,
                    "text": claim.text,
                    "reason": f"Hallucinated or unverified evidence chunk ID(s): {invalid_ids}",
                    "invalid_chunk_ids": invalid_ids,
                })
                continue

            if claim.claim_id in critic_unsupported_set:
                rejected_claims.append({
                    "claim_id": claim.claim_id,
                    "text": claim.text,
                    "reason": "Flagged as unsupported by Critic assessment",
                })
                continue

            if not claim.is_grounded:
                rejected_claims.append({
                    "claim_id": claim.claim_id,
                    "text": claim.text,
                    "reason": "Claim marked as ungrounded",
                })
                continue

            accepted_claims.append({
                "claim_id": claim.claim_id,
                "text": claim.text,
                "cited_chunk_ids": [str(c) for c in cited],
            })

        if claims and not accepted_claims:
            return ResolutionResult(
                status="INSUFFICIENT_EVIDENCE",
                final_answer=None,
                explanation="Candidate answer rejected: all atomic claims lacked valid supporting evidence.",
                rejected_claims=rejected_claims,
            )

        if rejected_claims:
            qualifications.append(f"{len(rejected_claims)} claim(s) lacked valid grounding and were excluded.")

        # =========================================================================
        # PHASE 3, 4, 5: Contradiction, Scope, Supersession, Authority
        # =========================================================================
        has_unresolvable_conflict = False
        unresolvable_topic = None

        for contradiction in critic_res.contradictions:
            # Check scope alignment
            if not contradiction.scope_aligned:
                qualifications.append(
                    f"Distinguished differentiated scopes for topic '{contradiction.topic}': "
                    "sources govern different entities, jurisdictions, or parameters."
                )
                continue

            # Scope-aligned conflict: gather passages
            conflict_passages: List[RetrievedPassage] = []
            for eid in contradiction.conflicting_evidence_ids:
                if eid in passage_by_id:
                    conflict_passages.append(passage_by_id[eid])

            if len(conflict_passages) < 2 and contradiction.positions:
                for pos in contradiction.positions:
                    if pos.passage_id in passage_by_id:
                        p_obj = passage_by_id[pos.passage_id]
                        if p_obj not in conflict_passages:
                            conflict_passages.append(p_obj)

            # Record positions for transparency
            if contradiction.positions:
                for pos in contradiction.positions:
                    p_obj = passage_by_id.get(pos.passage_id)
                    conflicting_positions.append({
                        "passage_id": str(pos.passage_id),
                        "source_title": pos.source_title or (p_obj.source_title if p_obj else None),
                        "version_identifier": pos.version_identifier or (p_obj.version_identifier if p_obj else None),
                        "quotation": pos.quotation,
                        "assertion": pos.assertion,
                        "authority_level": p_obj.authority_level if p_obj else None,
                        "effective_date": p_obj.effective_date if p_obj else None,
                        "upload_timestamp": p_obj.upload_timestamp if p_obj else None,
                    })
            else:
                for p in conflict_passages:
                    conflicting_positions.append({
                        "passage_id": str(p.chunk_id),
                        "source_title": p.source_title,
                        "version_identifier": p.version_identifier,
                        "quotation": p.text,
                        "assertion": f"Assertion in {p.version_identifier}",
                        "authority_level": p.authority_level,
                        "effective_date": p.effective_date,
                        "upload_timestamp": p.upload_timestamp,
                    })

            contradiction_resolved = False
            if len(conflict_passages) >= 2:
                p1, p2 = conflict_passages[0], conflict_passages[1]

                # 1. Supersession check
                is_resolved, explanation_str = self._evaluate_supersession(p1, p2, critic_res.staleness_findings)
                if is_resolved:
                    contradiction_resolved = True
                    qualifications.append(f"Resolved via explicit supersession: {explanation_str}")
                else:
                    # 2. Authority precedence check
                    rank1 = AUTHORITY_RANKS.get((p1.authority_level or "").upper(), 2)
                    rank2 = AUTHORITY_RANKS.get((p2.authority_level or "").upper(), 2)

                    if rank1 > rank2:
                        contradiction_resolved = True
                        qualifications.append(
                            f"Resolved via authority precedence: {p1.source_title} ({p1.authority_level}, rank {rank1}) "
                            f"takes precedence over {p2.source_title} ({p2.authority_level}, rank {rank2})."
                        )
                    elif rank2 > rank1:
                        contradiction_resolved = True
                        qualifications.append(
                            f"Resolved via authority precedence: {p2.source_title} ({p2.authority_level}, rank {rank2}) "
                            f"takes precedence over {p1.source_title} ({p1.authority_level}, rank {rank1})."
                        )

            if not contradiction_resolved:
                has_unresolvable_conflict = True
                unresolvable_topic = contradiction.topic

        if not has_unresolvable_conflict and not critic_res.contradictions and critic_res.verdict == CriticVerdict.CONFLICT:
            has_unresolvable_conflict = True
            unresolvable_topic = critic_res.explanation or "Contradictory policies detected"

        if has_unresolvable_conflict:
            return ResolutionResult(
                status="CONFLICT",
                final_answer=None,
                explanation=(
                    f"Cannot resolve with available information because equal authority, conflicting scopes, "
                    f"or no supersession metadata regarding '{unresolvable_topic or 'competing policy mandates'}'. "
                    "TruthGuard preserves the conflict and abstains from averaging contradictory claims."
                ),
                conflicting_positions=conflicting_positions,
                rejected_claims=rejected_claims,
                accepted_claims=accepted_claims,
                qualifications=qualifications,
            )

        # =========================================================================
        # PHASE 6: Assemble Answer & Grounding
        # =========================================================================
        has_qualifications = (
            bool(qualifications)
            or (critic_res.verdict == CriticVerdict.UNSUPPORTED and bool(accepted_claims))
            or bool(rejected_claims)
        )
        final_status = "ANSWERED_WITH_QUALIFICATION" if has_qualifications else "ANSWERED"

        # Construct final answer text
        if qualifications:
            formatted_answer = (
                candidate_answer.strip()
                + "\n\n[TruthGuard Qualifications:\n"
                + "\n".join(f"• {q}" for q in qualifications)
                + "]"
            )
            explanation = "Answer validated with deterministic qualifications: " + "; ".join(qualifications)
        else:
            formatted_answer = candidate_answer.strip()
            explanation = "Candidate answer is fully substantiated by consistent and authoritative evidence."

        # Evidence references
        evidence_refs: List[Dict[str, Any]] = []
        for p in passages:
            evidence_refs.append({
                "chunk_id": str(p.chunk_id),
                "source_title": p.source_title,
                "version_identifier": p.version_identifier,
                "effective_date": p.effective_date,
            })

        return ResolutionResult(
            status=final_status,
            final_answer=formatted_answer,
            explanation=explanation,
            conflicting_positions=conflicting_positions,
            rejected_claims=rejected_claims,
            accepted_claims=accepted_claims,
            qualifications=qualifications,
            evidence_references=evidence_refs,
        )

    def _evaluate_supersession(
        self,
        p1: RetrievedPassage,
        p2: RetrievedPassage,
        staleness_findings: List[StalenessFinding],
    ) -> tuple[bool, Optional[str]]:
        for sf in staleness_findings:
            if sf.has_explicit_supersession:
                if sf.newer_version_id == p1.version_id and sf.older_version_id == p2.version_id:
                    return True, f"Version {p1.version_identifier} supersedes Version {p2.version_identifier} because {sf.details}"
                if sf.newer_version_id == p2.version_id and sf.older_version_id == p1.version_id:
                    return True, f"Version {p2.version_identifier} supersedes Version {p1.version_identifier} because {sf.details}"

        # Direct metadata supersedes_version_id check
        d1 = p1.effective_date
        d2 = p2.effective_date

        p1_links_p2 = (p1.supersedes_version_id and p1.supersedes_version_id == p2.version_id) or \
                      (p2.superseded_by_id and p2.superseded_by_id == p1.version_id)
        if p1_links_p2:
            if not (d1 and d2 and d1 < d2):
                return True, (
                    f"Version {p1.version_identifier} (effective {p1.effective_date}) explicitly supersedes "
                    f"older Version {p2.version_identifier} (effective {p2.effective_date})."
                )

        p2_links_p1 = (p2.supersedes_version_id and p2.supersedes_version_id == p1.version_id) or \
                      (p1.superseded_by_id and p1.superseded_by_id == p2.version_id)
        if p2_links_p1:
            if not (d1 and d2 and d2 < d1):
                return True, (
                    f"Version {p2.version_identifier} (effective {p2.effective_date}) explicitly supersedes "
                    f"older Version {p1.version_identifier} (effective {p1.effective_date})."
                )

        # Check text mentions with flexible pattern
        v1_clean = (p1.version_identifier or "").strip().lstrip("vV")
        v2_clean = (p2.version_identifier or "").strip().lstrip("vV")

        if p1.text and v2_clean:
            pat2 = r'(?:supersedes?|amends?|replaces?)[^\.\n]*?\b(?:policy\s+|version\s+|v\.?\s*)?' + re.escape(v2_clean) + r'\b'
            if re.search(pat2, p1.text, re.IGNORECASE):
                return True, f"Version {p1.version_identifier} supersedes Version {p2.version_identifier} because passage text explicitly states supersession of {p2.version_identifier}."

        if p2.text and v1_clean:
            pat1 = r'(?:supersedes?|amends?|replaces?)[^\.\n]*?\b(?:policy\s+|version\s+|v\.?\s*)?' + re.escape(v1_clean) + r'\b'
            if re.search(pat1, p2.text, re.IGNORECASE):
                return True, f"Version {p2.version_identifier} supersedes Version {p1.version_identifier} because passage text explicitly states supersession of {p1.version_identifier}."

        return False, None
