# Formato do repositório

A loja não tem servidor. Todo o "backend" são dois recursos estáticos servidos pelo GitHub:

| Recurso | Onde vive | Servido por |
|---|---|---|
| Catálogo (`index.json`) | branch `main` do repo | `raw.githubusercontent.com` |
| Ícones (`.png`) | branch `main`, pasta `icons/` | `raw.githubusercontent.com` |
| APKs | **assets de Release** | `objects.githubusercontent.com` (CDN) |

## Por que os APKs vão em Releases e não no repo

Arquivos versionados no Git têm limite prático de 100 MB e ficam permanentemente no
histórico — cada versão nova de cada app incharia o clone para sempre. Assets de release
aceitam até 2 GB, são servidos por CDN, e podem ser removidos sem reescrever histórico.

## Schema do `index.json`

```json
{
  "schema": 1,
  "repo": {
    "name": "Loja Geely",
    "description": "Apps para a multimídia Flyme Auto",
    "updated": "2026-07-31T14:20:00Z"
  },
  "apps": [
    {
      "id": "com.exemplo.app",
      "name": "Nome exibido",
      "summary": "Uma linha, aparece no card do catálogo",
      "description": "Texto longo, aparece na tela de detalhe",
      "category": "Navegação",
      "author": "Quem publicou",
      "icon": "https://raw.githubusercontent.com/USUARIO/REPO/main/icons/com.exemplo.app.png",
      "versionName": "1.2.3",
      "versionCode": 10203,
      "minSdk": 21,
      "size": 12345678,
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "abis": ["arm64-v8a", "armeabi-v7a"],
      "url": "https://github.com/USUARIO/REPO/releases/download/apps/exemplo-1.2.3.apk",
      "added": "2026-07-31"
    }
  ]
}
```

### Campos que a loja usa para decidir coisas

- **`id`** — package name. É a chave: a loja compara com os apps instalados no aparelho
  para mostrar *Instalar* / *Atualizar* / *Abrir*.
- **`versionCode`** — inteiro. É o que define "tem atualização", nunca o `versionName`.
- **`minSdk`** — se for maior que o API level do aparelho, a loja mostra o app como
  incompatível em vez de deixar o usuário baixar 40 MB para falhar na instalação.
- **`abis`** — mesma lógica, para APKs com código nativo.
- **`sha256`** — conferido depois do download, antes de disparar a instalação. Sem isso,
  um download truncado vira uma tela de "app não instalado" sem explicação.
- **`size`** — permite mostrar barra de progresso real e avisar antes de gastar dados.

## Fluxo de publicação

1. Coloque os APKs numa pasta local.
2. Rode `tools/gerar-index.ps1` — ele lê os metadados de cada APK com `aapt2`, calcula o
   SHA-256 e monta o `index.json`.
3. Suba os APKs como assets de uma Release (a tag pode ser fixa, ex.: `apps`).
4. Commit do `index.json` e dos ícones na `main`.

O app só relê o `index.json`, então publicar uma versão nova é um commit.
