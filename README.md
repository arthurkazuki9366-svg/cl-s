# SharkClans — sistema de clãs para o SharkSMP

Plugin de clãs para **Paper 26.3**, sem dependências de outros plugins.
Código-fonte em Java 25 (`com.sharksmp.clans`), build com Maven.

> Esta versão (1.1.0) só carrega em **Paper 26.3 com Java 25**. Para servidores 1.20–1.21 use a versão 1.0.0 (Java 17).

## Como gerar o .jar

Requisitos: **JDK 25** e **Maven 3.9+** (com internet, para baixar a API do Paper).

    ./build.sh          # Linux/macOS
    build.bat           # Windows
    # ou: mvn clean package

O arquivo sai em `target/SharkClans-1.1.0.jar`. Copie para a pasta `plugins/` e reinicie o servidor.

Sem Java/Maven na máquina: suba esta pasta num repositório do GitHub — o workflow
`.github/workflows/build.yml` compila sozinho e disponibiliza o `.jar` em *Actions → Artifacts*.

## Comandos

| Comando | Função |
|---|---|
| `/clan` | Abre o painel (GUI) |
| `/clan criar <nome>` | Cria um clã |
| `/clan convidar <jogador>` | Convida (líder e sub-líderes) |
| `/clan aceitar [clã]` / `/clan recusar [clã]` | Responde a um convite |
| `/clan sair` | Sai do clã (o líder não pode) |
| `/clan expulsar <jogador>` | Expulsa membro (líder; sub-líder só membros comuns) |
| `/clan promover <jogador>` / `/clan rebaixar <jogador>` | Sub-líder (somente líder) |
| `/clan lider <jogador>` | Transfere a liderança (somente líder) |
| `/clan deletar` | Exclui o clã (somente líder) |
| `/clan info [clã]` | Informações, membros e cargos |
| `/clan listar [página]` | Lista todos os clãs |
| `/clan chat [mensagem]` | Alterna o chat exclusivo (ou envia uma mensagem só ao clã) |
| `/clan ajuda` | Ajuda |

Administração (permissão `sharkclans.admin`): `/clan admin deletar <clã>`,
`/clan admin expulsar <jogador>`, `/clan admin tag <clã> <tag>`,
`/clan admin lider <clã> <jogador>`, `/clan admin recarregar`.

## Permissões

| Permissão | Padrão | Descrição |
|---|---|---|
| `sharkclans.use` | todos | Usar o sistema de clãs |
| `sharkclans.create` | todos | Criar clãs |
| `sharkclans.admin` | OP | Comandos `/clan admin ...` |
| `sharkclans.*` | OP | Todas as anteriores |

## Arquivos

- `plugins/SharkClans/config.yml` — limite de membros/sub-líderes, tamanho de nome e tag, formato da tag, expiração de convites e **todas as mensagens** (pt-BR).
- `plugins/SharkClans/clans.yml` — dados dos clãs; salvo a cada alteração e ao desligar o servidor.

## Observações

- A tag aparece antes do nome no chat; o líder muda texto e cor da tag pelo painel (`/clan` → Configurar tag).
- Nome e tag são únicos (sem diferenciar maiúsculas/minúsculas). Se a tag automática colidir, é gerada uma variação numerada.
- Convites ficam em memória e expiram (padrão 60 s); não sobrevivem a reinício.
- Os textos da GUI (títulos/botões) estão no código; as mensagens de chat estão no `config.yml`.
