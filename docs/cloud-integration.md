# Evolução: enviar um álbum para uma nuvem

## Implementado nesta base

- Banco privado com IDs persistentes de mídias e álbuns, independentes dos nomes e caminhos públicos.
- `AlbumTransferSource`: snapshot dos arquivos ativos de um álbum, com ID, nome, MIME, tamanho e modificação.
- `open(item)`: leitura de um arquivo privado para envio, sem copiá-lo para MediaStore e sem expor caminhos de armazenamento ao provedor.
- Exclusão da lixeira e revalidação antes de abrir um item que foi movido, excluído ou alterado.
- Nenhum SDK de nuvem, conta vinculada ou permissão de rede no app atual.

## Próxima implementação

1. Menu de três pontos do álbum → Enviar para nuvem.
2. Escolher provedor (Google Drive, depois outros) e conta vinculada, ou vincular uma conta.
3. OAuth com navegador/SDK oficial e escopos mínimos; nunca coletar senha do serviço no app.
4. Escolher destino e confirmar quantidade/tamanho antes de iniciar.
5. Envio explícito e em segundo plano com WorkManager, progresso, cancelamento e retomada.
6. Persistir trabalhos por ID local do álbum/mídia, provedor, conta e ID remoto. Usar checksum para idempotência; nomes não são identificadores.
7. Separar `CloudDestination`/adaptadores por provedor da biblioteca local. Eles recebem metadados e streams via `AlbumTransferSource`.
8. Tokens protegidos pelo Android Keystore, desvinculação/revogação e tratamento de expiração.

O upload deve criar cópia remota e preservar o original privado. Exclusão local não
deve apagar automaticamente a nuvem (nem o inverso). Não adicionar sincronização
automática sem uma opção explícita. Avisar que a cópia remota passa a seguir as
regras e permissões do provedor. Adicionar INTERNET apenas ao implementar rede.

## Validação em dispositivo

- Instalar ao lado de uma galeria com fotos existentes: biblioteca inicial vazia, sem permissão de leitura de mídias.
- Foto, vídeo com áudio e câmera frontal/traseira; negar câmera/microfone deve explicar a ação sem travar.
- As capturas persistem após fechar/reabrir; não aparecem em Google Fotos/Galeria/MediaStore.
- Favoritar, mover/copiar entre álbuns, renomear, editar, extrair quadro, restaurar lixeira e excluir.
- Interromper uma gravação, sair para outro app, girar o dispositivo e testar falta de espaço; nunca publicar arquivo incompleto.
- Compartilhar/exportar somente após uma ação explícita; conferir que o original permanece privado.
- Snapshot de transferência exclui lixeira e não aceita mídia removida/movida após o snapshot.
