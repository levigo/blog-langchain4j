# blog-langchain4j
Some code snippets for my blogs. If you are interested in [langchain4j](https://github.com/langchain4j/langchain4j), I would recommend to take a look at [langchain4j examples](https://github.com/langchain4j/langchain4j-examples)

This project contains some spring boot junit tests for the langchain4j framework. Models for test can be configured via ``application.yaml``, those will be auto-pulled in the configured ollama instance by default if needed.

Main beef is in the test classes themselves. So check the src/test/java folder for various use cases. More examples can be found as mentioned in the langchain4j-examples directly.

Some of the tests include:
- simple RAG chat bot
- chat with memory (history)
- Java Pojo extraction
- Image recognition
- Tests with VectorDB (in-memory + external pgvector if enabled)
- FunctionCalling
- ...

In the ``docker`` folder, a ``docker-compose.yaml`` can be found to start a local [Ollama](https://ollama.com/) instance. Also a [PGVector Postgres DB](https://github.com/pgvector/pgvector) and [OpenWebUI](https://github.com/open-webui/open-webui) is contained.

# Running
As mentioned, simply run the test classes under src/test/java. If a local docker is running, the test should run without problems.
Required models will be pulled automatically if needed (this can be disabled via application.yaml).
