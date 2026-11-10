from app.ai import match_judge
from app.ai.client import AIUnavailable
from app.ai.match_judge import JudgedCandidate, JudgeOutput, clamp
from app.schemas.domain import MatchResult, MatchType
from tests.conftest import FakeLLM


def rule(kind, different=()):
    return MatchResult(match_type=kind, confidence=0.7, different_attributes=list(different),
                       unknown_attributes=["color"], reason="rule")


def ai(kind, confidence=0.95):
    return JudgedCandidate(index=0, match_type=kind, confidence=confidence, matched_attributes=[],
                           different_attributes=[], reason="ai")


def test_ai_can_confirm_a_likely_match_only_with_high_confidence():
    assert clamp(rule(MatchType.LIKELY), ai(MatchType.EXACT, 0.95)).match_type == MatchType.EXACT
    assert clamp(rule(MatchType.LIKELY), ai(MatchType.EXACT, 0.95)).decided_by == "ai"
    assert clamp(rule(MatchType.LIKELY), ai(MatchType.EXACT, 0.95)).confidence <= 0.9
    assert clamp(rule(MatchType.LIKELY), ai(MatchType.EXACT, 0.7)).match_type == MatchType.LIKELY


def test_ai_can_never_override_a_deterministic_conflict():
    for kind in (MatchType.SIMILAR, MatchType.DIFFERENT):
        result = clamp(rule(kind, ["volume_ml"]), ai(MatchType.EXACT, 1.0))
        assert result.match_type == kind and result.decided_by == "rules"
    assert clamp(rule(MatchType.LIKELY, ["model"]), ai(MatchType.EXACT, 1.0)).match_type == MatchType.LIKELY


def test_ai_can_always_downgrade():
    assert clamp(rule(MatchType.LIKELY), ai(MatchType.DIFFERENT)).match_type == MatchType.DIFFERENT
    assert clamp(rule(MatchType.EXACT), ai(MatchType.SIMILAR)).match_type == MatchType.SIMILAR


async def test_judge_falls_back_to_rules_when_ai_fails_or_misindexes():
    candidates = [("Anessa 60ml", rule(MatchType.LIKELY)), ("Anessa", rule(MatchType.LIKELY))]
    assert await match_judge.judge(FakeLLM({JudgeOutput: AIUnavailable("x")}), "ref", candidates) == [c[1] for c in candidates]
    out = JudgeOutput(results=[ai(MatchType.DIFFERENT), JudgedCandidate(
        index=7, match_type=MatchType.EXACT, confidence=1, matched_attributes=[], different_attributes=[], reason="?")])
    judged = await match_judge.judge(FakeLLM({JudgeOutput: out}), "ref", candidates)
    assert [j.match_type for j in judged] == [MatchType.DIFFERENT, MatchType.LIKELY]
    assert await match_judge.judge(FakeLLM(), "ref", []) == []
