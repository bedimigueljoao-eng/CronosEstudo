# CronosEstudo v1.6.0

## Lógica corrigida com base no modelo fornecido

A aplicação passa a distinguir explicitamente:

1. **Discipline**: identidade da disciplina.
2. **ScheduleSlot**: cada dia e horário independente no calendário.
3. **StudyLog**: histórico do que foi efetivamente estudado.

## Comportamento

- Adicionar outro dia/horário cria um novo slot com ID próprio.
- O slot já existente não é alterado.
- Editar altera somente o slot selecionado.
- Eliminar remove somente o slot selecionado.
- Tempo estudado associado a um slot eliminado é preservado como histórico.
- O alarme, o ícone e a interface de agenda/análise permanecem incluídos.

O GitHub Actions gera `CronosEstudo-v1.6.0-APK`.
