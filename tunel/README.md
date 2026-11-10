# Túnel próprio na Oracle Cloud (São Paulo)

Sua operadora não deixa ninguém de fora conectar direto no celular. A solução é ter uma máquina sua na nuvem que recebe os jogadores e repassa para o celular. A Oracle dá uma máquina grátis para sempre (plano **Always Free**) e tem região em **São Paulo**, então o ping fica baixo.

```
Celular (servidor Minecraft)  ⇄  Máquina na Oracle (frps)  ⇄  Amigos
```

O túnel usa o [frp](https://github.com/fatedier/frp), que é de código aberto. Nenhum site de terceiro fica no meio.

## Passo a passo

### 1. Pegue o script no app
No JavaWorld, abra a aba **Túnel**. O app já gera um token (a senha do túnel) e monta um script com ele. Toque em **Script de inicialização (cloud-init)** para copiar.

### 2. Crie a conta e a máquina
1. Crie a conta em <https://www.oracle.com/br/cloud/free/> e escolha **Brazil East (São Paulo)** como região inicial. Eles pedem um cartão só para verificar, o plano grátis não cobra.
2. No painel: **Compute → Instances → Create instance**.
3. Imagem: **Ubuntu** (22.04 ou 24.04). Formato: qualquer um marcado como *Always Free* (por exemplo VM.Standard.A1.Flex com 1 OCPU e 6 GB, ou VM.Standard.E2.1.Micro).
4. Em **Show advanced options → Management → Initialization script**, escolha **Paste cloud-init script** e cole o script que você copiou do app.
5. Crie a instância e anote o **Public IP address**.

### 3. Libere as portas na Oracle
A Oracle bloqueia tudo por padrão, mesmo com o script.
1. Na página da instância, clique na **Subnet** → **Security List** padrão → **Add Ingress Rules**.
2. Source CIDR: `0.0.0.0/0`, IP Protocol: **TCP**, Destination Port Range: `7000`.
3. Adicione outra regra igual com a porta `25565`.

### 4. Conecte o app
Na aba **Túnel**, cole o **IP público** da máquina e toque em **Salvar**. Toque em **Testar conexão**: quando aparecer **No ar**, está pronto. A partir daí, o túnel liga sozinho junto com o servidor.

Seus amigos entram no Minecraft em **Multijogador → Adicionar servidor**, usando o IP da máquina.

## Já tem uma máquina?
Rode por SSH o comando que aparece no app (**Comando por SSH**). Ele é algo assim:

```bash
curl -fsSL https://raw.githubusercontent.com/pauloestefani/JavaWorld/main/tunel/instalar-frps.sh | sudo TOKEN='SEU_TOKEN' bash
```

## Problemas comuns
- **"Não consegui falar com a máquina"**: a porta 7000 não foi liberada na Security List, ou o IP está errado.
- **"O token não bate"**: o token do app é diferente do que foi usado no script. Rode o comando por SSH de novo com o token do app.
- **Amigo não conecta, mas o túnel está "No ar"**: falta liberar a porta 25565 na Security List.
- Para ver o que está acontecendo na máquina: `journalctl -u frps -f`.
