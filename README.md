# blog-langchain4j
Some code snippets for my blogs. If you are interested in [langchain4j](https://github.com/langchain4j/langchain4j), I would recommend to take a look at [langchain4j examples](https://github.com/langchain4j/langchain4j-examples)

This project contains some spring boot junit tests for the langchain4j framework. Models for test can be configured via ``application.yaml``, those will be auto-pulled in the configured ollama instance by default if needed.

Main beef is in the test classes themselves. So check the src/test/java folder for various use cases. More examples can be found as mentioned in the langchain4j-examples directly.

In the ``docker`` folder, a ``docker-compose.yaml`` can be found to start a local [Ollama](https://ollama.com/) instance. Also a [PGVector Postgres DB](https://github.com/pgvector/pgvector) and [OpenWebUI](https://github.com/open-webui/open-webui) is contained.
