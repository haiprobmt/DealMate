from app.ai.client import StructuredLLM, get_llm
from app.connectors.registry import ConnectorRegistry, get_registry


def registry_dep() -> ConnectorRegistry:
    return get_registry()


def llm_dep() -> StructuredLLM | None:
    return get_llm()
