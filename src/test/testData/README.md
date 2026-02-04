# Test Data Directory
# ===================
# This directory contains test fixtures and sample data for Sidekick tests.
#
# Subdirectories:
# - ollama/     - Mock Ollama API responses (JSON)
# - context/    - Sample code files for context injection tests
# - chat/       - Chat history fixtures
#
# Files placed here can be accessed in tests via:
# ```kotlin
# val file = File(testDataPath, "ollama/list-models.json")
# ```
