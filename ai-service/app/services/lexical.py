"""Lightweight lexical and keyword scoring engine for hybrid retrieval."""

import re
from typing import List, Set


# Standard English stopwords to filter out of lexical scoring
STOPWORDS: Set[str] = {
    "a", "an", "the", "and", "or", "but", "if", "then", "else", "when",
    "at", "by", "for", "with", "about", "against", "between", "into",
    "through", "during", "before", "after", "above", "below", "to", "from",
    "up", "down", "in", "out", "on", "off", "over", "under", "again",
    "further", "then", "once", "here", "there", "where", "why", "how",
    "all", "any", "both", "each", "few", "more", "most", "other", "some",
    "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too",
    "very", "s", "t", "can", "will", "just", "don", "should", "now",
    "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
    "do", "does", "did", "what", "which", "who", "whom", "this", "that"
}


def stem_token(word: str) -> str:
    """Lightweight rule-based suffix normalization for keyword matching."""
    w = word.lower().strip()
    if len(w) <= 3:
        return w

    # Strip common suffixes
    if w.endswith("sses"):
        return w[:-2]
    if w.endswith("ies"):
        return w[:-3] + "y"
    if w.endswith("ations"):
        return w[:-6]
    if w.endswith("ation"):
        return w[:-5]
    if w.endswith("tions"):
        return w[:-5]
    if w.endswith("tion"):
        return w[:-4]
    if w.endswith("ing") and len(w) > 5:
        return w[:-3]
    if w.endswith("ed") and len(w) > 4:
        return w[:-2]
    if w.endswith("es") and len(w) > 4:
        return w[:-2]
    if w.endswith("s") and not w.endswith("ss") and len(w) > 3:
        return w[:-1]
    if w.endswith("ment") and len(w) > 6:
        return w[:-4]
    if w.endswith("e") and len(w) > 4:
        return w[:-1]

    return w


def tokenize_text(text: str, filter_stopwords: bool = True) -> List[str]:
    """Tokenize and stem text into words."""
    raw_tokens = re.findall(r"\b[a-zA-Z0-9_\-\.]+\b", text.lower())
    tokens = []
    for t in raw_tokens:
        clean = t.strip(".-_")
        if not clean:
            continue
        if filter_stopwords and clean in STOPWORDS:
            continue
        tokens.append(stem_token(clean))
    return tokens


class LexicalScorer:
    """Computes normalized keyword and phrase match scores in [0.0, 1.0]."""

    @classmethod
    def score(cls, query: str, passage: str) -> float:
        """
        Calculate lexical relevance between a query and candidate passage.
        Guarantees:
        - Output is in [0.0, 1.0].
        - Completely irrelevant passages score 0.0.
        - Exact term and phrase matches are rewarded.
        """
        if not query or not query.strip() or not passage or not passage.strip():
            return 0.0

        query_clean = query.strip().lower()
        passage_clean = passage.strip().lower()

        # 1. Check exact raw substring match bonus
        exact_phrase_bonus = 0.0
        if len(query_clean) > 3 and query_clean in passage_clean:
            exact_phrase_bonus = 0.35

        # 2. Tokenized match
        q_tokens = tokenize_text(query, filter_stopwords=True)
        # If all query tokens were stopwords (e.g. "what is that"), fall back to unfiltered tokens
        if not q_tokens:
            q_tokens = tokenize_text(query, filter_stopwords=False)
        if not q_tokens:
            return 0.0

        p_tokens = tokenize_text(passage, filter_stopwords=True)
        if not p_tokens:
            return exact_phrase_bonus

        p_token_set = set(p_tokens)
        matched_tokens = [t for t in q_tokens if t in p_token_set]

        if not matched_tokens:
            return min(1.0, exact_phrase_bonus)

        # Term coverage: fraction of query terms present in passage
        coverage = len(set(matched_tokens)) / len(set(q_tokens))

        # Term frequency: density of matched tokens in passage
        match_frequency = sum(p_tokens.count(t) for t in set(matched_tokens))
        density = min(1.0, match_frequency / (len(p_tokens) + 5))

        # Combined lexical score
        base_score = 0.65 * coverage + 0.20 * density + exact_phrase_bonus
        return min(1.0, max(0.0, round(base_score, 4)))
