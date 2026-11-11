# JavaWorld

Servidor de **Minecraft Java** rodando direto no celular Android. Sem PC, sem site de terceiro no meio.

**[Baixar o APK mais recente](https://github.com/pauloestefani/JavaWorld/releases/latest/download/JavaWorld.apk)**

## O que ele faz
- **Escolhe a versão**: qualquer versão do Minecraft com servidor oficial, das antigas até a mais nova.
- **Vanilla, Fabric, Forge ou NeoForge**: começa vanilla e, se quiser mods depois, converte sem perder o mundo.
- **Mods**: busca e instala direto do [Modrinth](https://modrinth.com) (com as dependências), ou adiciona um `.jar` do celular. Tem um botão de "pacote de desempenho" (Lithium, FerriteCore etc.).
- **Java automático**: baixa sozinho o Java certo para cada versão (8, 17, 21 ou 25).
- **Monitor do celular**: processador, núcleos e frequência de cada um, RAM, bateria e temperatura, e quanto o servidor está usando de CPU e RAM.
- **Console**: vê o log ao vivo e manda comandos, com atalhos (dar OP, virar dia, etc.).
- **Ajustes**: modo de jogo, dificuldade, PvP, whitelist, modo original/pirata, distância de visão, RAM.
- **Mundos**: exporta e importa o mundo em `.zip` (dá pra trazer um mundo do singleplayer).
- **Tátão**: a aba "Tátão" é o [Tátão](https://github.com/pauloestefani/T-t-o) dentro do app (sala, voz, chat, amigos). Com o servidor ligado e você numa sala, ele aparece sozinho pros amigos que estão na sala pelo Tátão do PC, e a conexão passa pela própria sala. Não precisa de nuvem nem de VPN.
- **Túnel próprio**: para amigos de fora da sua rede entrarem, usando uma máquina sua grátis na Oracle Cloud em São Paulo. Veja o [guia do túnel](tunel/README.md).
- Continua rodando com a tela desligada (serviço em primeiro plano).

## Como funciona por dentro
O Android não deixa um app rodar programas baixados. Então o APK leva um pequeno launcher nativo (`app/src/main/cpp/launcher.c`) que carrega o Java baixado (OpenJDK compilado para Android) e inicia o servidor normalmente, como o comando `java -jar server.jar`.

O túnel usa o [frp](https://github.com/fatedier/frp): o celular conecta na sua máquina na nuvem e ela repassa os jogadores.

## Compilação
O APK é gerado automaticamente pelo GitHub Actions a cada mudança (`.github/workflows/build.yml`): compila, testa num emulador Android (liga um servidor de verdade e testa o túnel) e publica em **Releases**.

## Créditos e licenças
- Java para Android: [angelauramc-openjdk-build](https://github.com/AngelAuraMC/angelauramc-openjdk-build) (OpenJDK, GPLv2 com Classpath Exception).
- Túnel: [frp](https://github.com/fatedier/frp) (Apache 2.0).
- Mods: API do [Modrinth](https://docs.modrinth.com).
- Minecraft é uma marca da Mojang/Microsoft. Este app não é oficial. Ao criar um servidor você aceita o [EULA do Minecraft](https://aka.ms/MinecraftEULA).
