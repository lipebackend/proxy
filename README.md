# Proxy TCP em Java

Projeto de estudo para construir um proxy TCP do zero e entender sockets, concorrência e gerenciamento de conexões. Surgiu de uma ideia de proxy para Minecraft e seguiu como um experimento independente para aprendizado e portfólio.

## Objetivo

Implementar um intermediário que aceite uma conexão, abra outra até um servidor de destino configurado e encaminhe os dados nos dois sentidos.

```text
Cliente ⇄ Proxy TCP ⇄ Servidor de destino
```

O primeiro marco é enviar uma mensagem a um servidor local através do proxy e receber a resposta. Esse encaminhamento não exige interpretar o protocolo da aplicação.

## Estado atual

**Em desenvolvimento: o projeto ainda não compila nem encaminha tráfego.**

O código contém rascunhos de:

- Aceitação de conexões com `ServerSocket`.
- Atendimento de clientes com virtual threads.
- Controle de conexões por IP com `ConcurrentHashMap` e `AtomicInteger`.

Existem pendências na entrada `Main`, no tipo usado pelo construtor de `RateLimiter` e no teste inicial, que ainda referencia uma saudação inexistente. O limite por IP começa em zero, e a liberação das conexões ainda não está integrada ao atendimento.

Autenticação e encaminhamento aparecem como um TODO; nenhum dos dois está implementado. Não há integração com Minecraft ou banco de dados.

## Tecnologias

- Java 21 como versão alvo.
- Maven.
- JUnit 5 e AssertJ para testes.
- APIs de rede e concorrência da biblioteca padrão do Java.

## Estrutura

```text
src/
├── main/java/com/lipe/proxy/
│   ├── Main.java                 # Rascunho do ponto de entrada
│   └── infra/
│       ├── ProxyServer.java      # Aceitação e atendimento de conexões
│       └── RateLimiter.java      # Rascunho do controle de conexões por IP
└── test/java/com/lipe/proxy/
    └── MainTest.java             # Teste inicial a ser atualizado
```

## Build e testes

Requer um JDK compatível com Java 21 e Maven instalado. O comando de build e testes é:

```bash
mvn test
```

No estado atual, os erros de compilação mencionados acima impedem a execução dos testes. Ainda não há um procedimento funcional para iniciar o proxy.

## Próximos passos

- [ ] Corrigir a compilação e atualizar o teste inicial.
- [ ] Configurar a porta de entrada e o endereço do destino.
- [ ] Implementar o encaminhamento bidirecional.
- [ ] Tratar encerramentos, falhas e liberação do contador por IP.
- [ ] Validar o fluxo com um cliente e um servidor locais.
- [ ] Adicionar testes de integração para troca de dados e desconexões.

Autenticação e roteamento entre múltiplos destinos ficam para uma etapa posterior, conforme o cenário de uso escolhido.
