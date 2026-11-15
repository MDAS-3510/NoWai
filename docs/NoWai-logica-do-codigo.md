# NoWai: guia da lógica do código

Este documento explica **como o NoWai funciona por dentro**: a arquitetura, as regras de negócio e o que cada arquivo e cada linha faz. Ele cobre o que já foi construído nos Passos 1 a 3 (camada de dados e lógica de recorrência). Conforme o app crescer, novas seções serão adicionadas.

> Pacote real do projeto: `com.cursoandroid.nowai`. Todos os arquivos de dados ficam em `com.cursoandroid.nowai.data`.

---

## 1. O que é o NoWai

Um app Android minimalista para organizar **tarefas domésticas** e **temas de estudo** com foco em **recorrência**: para cada item, o app mostra *há quanto tempo foi feito* e *quando vence de novo*.

Princípios: poucas telas, sem login, sem anúncios, funciona offline e prioriza rapidez de uso.

---

## 2. Arquitetura: MVVM + Repository

Arquitetura é a **forma de organizar o código em camadas**, cada uma com uma responsabilidade. O NoWai usa **MVVM** (Model, View, ViewModel) com um **Repository** no meio.

```
┌─────────────────────────────────────────────┐
│  VIEW  (Fragment + XML + RecyclerView)      │  ← o que o usuário vê e toca
└──────────────────────┬──────────────────────┘
                       │ observa dados / envia ações
┌──────────────────────▼──────────────────────┐
│  VIEWMODEL                                  │  ← estado da tela e lógica de apresentação
└──────────────────────┬──────────────────────┘
                       │ pede / salva dados
┌──────────────────────▼──────────────────────┐
│  REPOSITORY  (NoWaiRepository)              │  ← única porta de entrada para os dados
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│  ROOM  (Entities + DAOs + AppDatabase)      │  ← banco SQLite local
└─────────────────────────────────────────────┘
```

### O que cada camada faz

| Camada | Responsabilidade | Regra de ouro |
|---|---|---|
| **View** | Mostrar dados e capturar toques | Não tem regra de negócio, só desenha |
| **ViewModel** | Guardar o estado da tela e reagir às ações do usuário | Não conhece Views nem Context de tela |
| **Repository** | Decidir de onde vêm os dados | O ViewModel não sabe que Room existe |
| **Room** | Ler e gravar no SQLite | Nenhuma lógica de tela aqui |

### Por que separar assim?

- **Testabilidade**: a lógica de recorrência (`RecurrenceLogic`) não depende de Android, então testamos em milissegundos.
- **Manutenção**: trocar o banco, ou adicionar uma API no futuro, mexe só no Repository.
- **Aprendizado**: cada peça tem uma única função, e isso facilita entender o todo.

### Onde estamos hoje

| Camada | Status |
|---|---|
| Room (Entities, DAOs, Database) | ✅ pronto |
| Repository | ✅ pronto |
| Lógica de recorrência e tempo | ✅ pronta e testada |
| ViewModel | ⏳ Passo 4 |
| View (telas) | ⏳ Passo 4 |

### Conceito-chave: fluxo reativo

Os DAOs devolvem `Flow<List<Item>>` em vez de uma lista simples. Um `Flow` é um **fluxo de dados que emite um novo valor sempre que a tabela muda**. Quando a tela estiver pronta, ela vai "assinar" esse fluxo, e qualquer inserção, edição ou exclusão atualiza a lista sozinha, sem precisar recarregar nada manualmente.

---

## 3. Regras de negócio

São as decisões sobre **como o app deve se comportar**, independentes de tecnologia.

| # | Regra | Onde é implementada |
|---|---|---|
| 1 | Existem dois tipos de item: **tarefa** (`TASK`) e **tema de estudo** (`STUDY`) | `ItemType` em `Entities.kt` |
| 2 | Tarefas e temas usam a **mesma tabela** e a mesma lógica de repetição | `Item` em `Entities.kt` |
| 3 | A repetição é "a cada N dias/semanas/meses/anos" | `repeatEvery` + `repeatUnit` |
| 4 | Um item pode **não repetir** (os dois campos ficam nulos) | `RecurrenceLogic.complete()` |
| 5 | **Ao concluir, o próximo vencimento conta a partir do momento da conclusão**, e não da data em que deveria ter sido feito | `RecurrenceLogic.complete()` |
| 6 | Itens concluídos que **não repetem** ficam sem novo vencimento | `RecurrenceLogic.complete()` |
| 7 | Na ordenação por relevância, **o que vence primeiro aparece no topo** | `ItemDao.observeByRelevance()` |
| 8 | Itens **sem vencimento vão para o final** da lista | `ORDER BY nextDueAt IS NULL, ...` |
| 9 | **Apagar uma categoria não apaga os itens**, eles ficam sem categoria | `ForeignKey.SET_NULL` |
| 10 | A busca procura o texto no **título** do item | `LIKE '%' \|\| :search \|\| '%'` |
| 11 | Categorias são listadas em **ordem alfabética**, sem diferenciar maiúsculas | `COLLATE NOCASE` |
| 12 | Os textos de tempo são "hoje", "há X dias/semanas/meses/anos", "vence em ...", "atrasado há ..." | `TimeFormatter` |

**Exemplo da regra 5.** "Trocar lençóis" repete a cada 2 semanas e venceu dia 1. Você só faz no dia 5. O próximo vencimento é dia **19** (5 + 14 dias), e não dia 15. Isso combina com a ideia central do app: mostrar *há quanto tempo* você fez de fato.

---

## 4. Estrutura de arquivos

```
app/src/main/java/com/cursoandroid/nowai/
├── MainActivity.kt
└── data/
    ├── Entities.kt            ← o que é guardado (tabelas)
    ├── Converters.kt          ← ensina o Room a guardar enums
    ├── ItemDao.kt             ← operações na tabela de itens
    ├── CategoryDao.kt         ← operações na tabela de categorias
    ├── AppDatabase.kt         ← o banco em si
    ├── NoWaiRepository.kt     ← porta de entrada para os dados
    ├── RecurrenceLogic.kt     ← regra de recorrência
    └── TimeFormatter.kt       ← textos "há X / vence em Y"

app/src/test/java/com/cursoandroid/nowai/data/
└── RecurrenceLogicTest.kt     ← testes unitários
```

---

## 5. Arquivo por arquivo

### 5.1 `Entities.kt`: o modelo de dados

Define **o que o app guarda**. Cada `@Entity` vira uma tabela no SQLite.

```kotlin
package com.cursoandroid.nowai.data
```
- Declara em qual pacote o arquivo vive. Arquivos do mesmo pacote enxergam uns aos outros sem `import`. Se o pacote estiver errado, aparece o erro `Unresolved reference`.

```kotlin
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
```
- Traz as anotações do Room que usaremos: `@Entity` (marca a tabela), `ForeignKey` (relação entre tabelas), `Index` (índice de busca rápida) e `@PrimaryKey` (identificador único).

```kotlin
enum class ItemType { TASK, STUDY }
enum class RepeatUnit { DAYS, WEEKS, MONTHS, YEARS }
```
- `enum class` cria um tipo com valores fixos.
- `ItemType`: um item é uma tarefa (`TASK`) ou um tema de estudo (`STUDY`).
- `RepeatUnit`: a unidade de repetição. Usar enum evita erros de digitação (`"semana"` vs `"semanas"`) e o compilador avisa se esquecermos algum caso.

```kotlin
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ItemType
)
```
- `@Entity(tableName = "categories")`: esta classe vira a tabela `categories`.
- `data class`: classe feita para guardar dados. O Kotlin gera sozinho `equals()`, `hashCode()`, `toString()` e `copy()`.
- `@PrimaryKey(autoGenerate = true)`: o `id` é a chave única, e o Room o gera automaticamente (1, 2, 3...).
- `val id: Long = 0`: o valor padrão `0` significa "ainda não salvo". Ao inserir, o Room troca por um id real.
- `val name: String`: o nome da categoria (ex.: "DevOps"). Não tem valor padrão, então é obrigatório.
- `val type: ItemType`: diz se a categoria é de tarefas ou de estudos.
- `val` significa que a propriedade é **imutável**. Para "alterar" um objeto, criamos uma cópia com `copy()`.

```kotlin
@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
```
- `tableName = "items"`: nome da tabela.
- `foreignKeys`: declara que `Item.categoryId` aponta para `Category.id`. É o que liga um item à sua categoria.
  - `entity = Category::class`: a tabela "pai".
  - `parentColumns = ["id"]`: a coluna na tabela pai.
  - `childColumns = ["categoryId"]`: a coluna correspondente em `items`.
  - `onDelete = ForeignKey.SET_NULL`: **se a categoria for apagada, o `categoryId` dos itens vira `null`**, e os itens permanecem. Essa é a regra de negócio 9.
- `indices = [Index("categoryId")]`: cria um índice para filtrar itens por categoria mais rápido. O Room também recomenda índices em chaves estrangeiras.

```kotlin
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: ItemType,
    val categoryId: Long? = null,
    val notes: String = "",
    val repeatEvery: Int? = null,
    val repeatUnit: RepeatUnit? = null,
    val lastDoneAt: Long? = null,
    val nextDueAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

| Campo | Significado |
|---|---|
| `id` | Identificador único gerado pelo Room |
| `title` | O que é o item ("Trocar lençóis", "O que é Sprint?") |
| `type` | Tarefa ou estudo |
| `categoryId: Long?` | A categoria do item. O `?` permite `null` (item sem categoria) |
| `notes` | Anotações. Vazio por padrão. É o campo usado nos temas de estudo |
| `repeatEvery: Int?` | O número da repetição (o "2" de "a cada 2 semanas"). `null` = não repete |
| `repeatUnit: RepeatUnit?` | A unidade (o "semanas"). `null` = não repete |
| `lastDoneAt: Long?` | Quando foi feito pela última vez. `null` = nunca feito |
| `nextDueAt: Long?` | Quando vence de novo. `null` = sem vencimento |
| `createdAt` | Quando o item foi criado. O padrão é "agora" |

**Por que datas como `Long`?** São *epoch milliseconds*: os milissegundos desde 01/01/1970. É um número simples, fácil de comparar, de ordenar no SQL e de converter para data legível quando necessário.

**Por que guardar `nextDueAt` pronto no banco?** Se calculássemos o vencimento na hora de exibir, a ordenação teria que ser feita no código. Com o valor salvo, a ordenação por "o que vence primeiro" vira uma simples consulta SQL.

**Por que uma tabela só para tarefas e estudos?** Os dois têm os mesmos campos. Duas tabelas duplicariam código e queries. O campo `type` diferencia.

---

### 5.2 `Converters.kt`: ensinando o Room a guardar enums

O SQLite só entende números, textos e bytes. Ele não sabe o que é um `ItemType`. Os conversores traduzem nos dois sentidos.

```kotlin
class Converters {
    @TypeConverter fun fromItemType(v: ItemType): String = v.name
    @TypeConverter fun toItemType(v: String): ItemType = ItemType.valueOf(v)

    @TypeConverter fun fromUnit(v: RepeatUnit?): String? = v?.name
    @TypeConverter fun toUnit(v: String?): RepeatUnit? = v?.let { RepeatUnit.valueOf(it) }
}
```

- `@TypeConverter`: avisa o Room de que a função é um tradutor.
- `fromItemType`: **ao gravar**, converte `ItemType.TASK` no texto `"TASK"` via `.name`.
- `toItemType`: **ao ler**, converte o texto `"TASK"` de volta em `ItemType.TASK` via `valueOf()`.
- `fromUnit` e `toUnit` fazem o mesmo para `RepeatUnit`, mas aceitam `null`, porque `repeatUnit` é opcional.
- `v?.name`: o `?.` é a **chamada segura**. Se `v` for `null`, devolve `null` em vez de dar erro.
- `v?.let { RepeatUnit.valueOf(it) }`: só executa o bloco se `v` não for `null`. O `it` é o valor dentro do `let`.

O Room só passa a usar essa classe porque o `AppDatabase` tem a anotação `@TypeConverters(Converters::class)`.

---

### 5.3 `ItemDao.kt`: operações na tabela de itens

**DAO** significa *Data Access Object*. É uma **interface** em que você descreve as operações, e o Room (via KSP) **gera o código** que as executa.

```kotlin
@Dao
interface ItemDao {
```
- `@Dao` registra a interface como DAO. Ela não tem corpo nas funções, porque o Room escreve a implementação em tempo de compilação.

```kotlin
    @Insert
    suspend fun insert(item: Item): Long
```
- `@Insert`: o Room gera o `INSERT INTO items ...`.
- `suspend`: a função roda **fora da thread principal**, dentro de uma coroutine. O Room proíbe acesso ao banco na thread da interface, porque isso travaria a tela.
- Retorna `Long`: o **id** gerado para o novo item.

```kotlin
    @Update
    suspend fun update(item: Item)
```
- Atualiza a linha cujo `id` bate com o do `item` recebido. Usamos isso para salvar uma edição ou uma conclusão.

```kotlin
    @Delete
    suspend fun delete(item: Item)
```
- Apaga a linha correspondente ao `item`.

```kotlin
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?
```
- `@Query`: escrevemos o SQL à mão.
- `:id` é o parâmetro da função inserido na consulta.
- Retorna `Item?` porque o item pode não existir. Nesse caso volta `null`.

```kotlin
    @Query("""
        SELECT * FROM items
        WHERE title LIKE '%' || :search || '%'
        ORDER BY nextDueAt IS NULL, nextDueAt ASC
    """)
    fun observeByRelevance(search: String): Flow<List<Item>>
```
Esta é a consulta mais importante até agora. Linha a linha:
- `SELECT * FROM items`: traz todas as colunas de todos os itens.
- `WHERE title LIKE '%' || :search || '%'`: filtra pelo título.
  - `||` concatena textos no SQL.
  - `%` é o curinga ("qualquer coisa").
  - Se `search` for `"lençol"`, vira `LIKE '%lençol%'`, e acha qualquer título que contenha o texto.
  - Se `search` for vazio (`""`), vira `LIKE '%%'`, e **todos** os itens passam. Por isso a busca vazia lista tudo.
- `ORDER BY nextDueAt IS NULL, nextDueAt ASC`: ordena em dois critérios.
  1. `nextDueAt IS NULL` vale `0` (falso, tem data) ou `1` (verdadeiro, sem data). Como 0 vem antes de 1, **itens com data vêm primeiro**.
  2. `nextDueAt ASC` ordena as datas **da mais próxima para a mais distante**. Itens atrasados (data no passado) ficam no topo.
- `fun observeByRelevance(...)`: **não** é `suspend`, porque retorna um `Flow`. O `Flow` já cuida de rodar fora da thread principal.
- `Flow<List<Item>>`: emite a lista inteira toda vez que a tabela `items` mudar.

---

### 5.4 `CategoryDao.kt`: operações na tabela de categorias

```kotlin
@Dao
interface CategoryDao {
    @Insert  suspend fun insert(category: Category): Long
    @Update  suspend fun update(category: Category)
    @Delete  suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>
}
```
- `insert`, `update` e `delete`: idênticos aos do `ItemDao`, aplicados a categorias. `update` serve para **renomear**, e `delete` dispara o `SET_NULL` nos itens.
- `ORDER BY name COLLATE NOCASE`: ordem alfabética **sem diferenciar maiúsculas de minúsculas**. Sem isso, "agile" ficaria depois de "Zeta", porque letras maiúsculas vêm antes das minúsculas no SQLite.
- `Flow<List<Category>>`: a lista de categorias se atualiza sozinha quando o usuário cria, renomeia ou apaga uma.

---

### 5.5 `AppDatabase.kt`: o banco de dados

```kotlin
@Database(entities = [Category::class, Item::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
```
- `@Database(...)`: declara o banco.
  - `entities`: as tabelas que existem. **Toda `@Entity` precisa estar listada aqui.**
  - `version = 1`: a versão do esquema. Quando mudarmos as tabelas no futuro, aumentamos o número e escrevemos uma *migração*.
  - `exportSchema = false`: não gera o arquivo JSON do esquema. Para um projeto pessoal é aceitável.
- `@TypeConverters(Converters::class)`: ativa os conversores de enum.
- `abstract class ... : RoomDatabase()`: o Room gera a implementação concreta.

```kotlin
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
```
- Expõem os DAOs. Quem tem o banco chama `db.itemDao()` para acessar a tabela de itens.

```kotlin
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
```
- `companion object` guarda membros que pertencem à **classe**, e não a cada objeto (o equivalente ao `static` do Java).
- `INSTANCE` é a única cópia do banco, e começa `null`.
- `@Volatile` garante que, se uma thread atribuir o valor, **as outras vejam imediatamente**.

```kotlin
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nowai.db"
                ).build().also { INSTANCE = it }
            }
```
Este é o padrão **Singleton**: o app inteiro usa **uma única** instância do banco.
- `INSTANCE ?: ...`: o operador Elvis. Se `INSTANCE` já existe, devolve ele. Se for `null`, executa o que vem depois.
- `synchronized(this) { ... }`: só uma thread por vez entra no bloco. Isso impede que duas threads criem dois bancos ao mesmo tempo.
- `context.applicationContext`: usa o contexto do **app**, e não o da tela. O contexto de uma tela deixaria a Activity "presa" na memória depois de ser fechada (vazamento de memória).
- `AppDatabase::class.java`: a classe do banco.
- `"nowai.db"`: o nome do arquivo SQLite no aparelho.
- `.build()`: cria o banco.
- `.also { INSTANCE = it }`: guarda o banco criado em `INSTANCE` e o devolve.

---

### 5.6 `NoWaiRepository.kt`: a porta de entrada dos dados

```kotlin
class NoWaiRepository(private val db: AppDatabase) {
    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()
```
- Recebe o banco no construtor (isso se chama **injeção de dependência**: a classe não cria o banco, ela o recebe pronto). Facilita trocar por um banco falso em testes.
- Guarda os dois DAOs em propriedades privadas.

```kotlin
    fun observeItems(search: String = ""): Flow<List<Item>> =
        itemDao.observeByRelevance(search)
    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
```
- Funções de **leitura**. Devolvem os fluxos dos DAOs. O `search` tem valor padrão `""`, ou seja, sem filtro.

```kotlin
    suspend fun addItem(item: Item) = itemDao.insert(item)
    suspend fun updateItem(item: Item) = itemDao.update(item)
    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    suspend fun addCategory(category: Category) = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
```
- Funções de **escrita**. Por enquanto só repassam a chamada para o DAO.
- Parecem redundantes agora, mas o Repository é o lugar certo para crescer. No futuro, regras que envolvem várias tabelas (como "mover item entre categorias") ficam aqui, e o ViewModel não precisa saber os detalhes.

---

### 5.7 `RecurrenceLogic.kt`: a regra de recorrência

É o **coração do app**. Não depende de Android nem de banco, só de datas. Por isso é fácil de testar.

```kotlin
object RecurrenceLogic {
```
- `object` cria um **singleton**. Não precisamos instanciar, e chamamos direto: `RecurrenceLogic.complete(item)`.

```kotlin
    fun calculateNextDue(from: Long, every: Int, unit: RepeatUnit): Long {
```
- Recebe o ponto de partida (`from`, em milissegundos), o número (`every`) e a unidade (`unit`). Devolve o novo vencimento, também em milissegundos.

```kotlin
        val date = Instant.ofEpochMilli(from)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
```
- `Instant.ofEpochMilli(from)`: transforma o número em um instante no tempo.
- `.atZone(ZoneId.systemDefault())`: aplica o **fuso horário do aparelho**.
- `.toLocalDateTime()`: obtém data e hora "de relógio" (ex.: 15/11/2026 16:03). Precisamos disso para somar dias ou meses do jeito que as pessoas entendem.

```kotlin
        val next = when (unit) {
            RepeatUnit.DAYS -> date.plusDays(every.toLong())
            RepeatUnit.WEEKS -> date.plusWeeks(every.toLong())
            RepeatUnit.MONTHS -> date.plusMonths(every.toLong())
            RepeatUnit.YEARS -> date.plusYears(every.toLong())
        }
```
- `when` é o "switch" do Kotlin. Como `unit` é um enum, o compilador **exige que todos os casos sejam tratados**.
- `plusDays`, `plusWeeks`, `plusMonths` e `plusYears` somam a quantidade na unidade certa.
- `every.toLong()`: essas funções esperam `Long`, e `every` é `Int`.
- Vantagem do `java.time`: `plusMonths` lida com meses de tamanhos diferentes. 31/jan + 1 mês = 28/fev (ou 29 em ano bissexto), sem você programar isso.

```kotlin
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
```
- Faz o caminho de volta: data de relógio → com fuso → instante → milissegundos.

```kotlin
    fun complete(item: Item, now: Long = System.currentTimeMillis()): Item {
```
- Recebe um item e o "agora" e devolve **outro** item.
- `now` tem valor padrão (o relógio real). Nos testes passamos uma data fixa, e isso torna o resultado previsível.

```kotlin
        val every = item.repeatEvery
        val unit = item.repeatUnit
```
- Copia os campos para variáveis locais. Isso permite que o Kotlin garanta que não são `null` dentro do `if` (o chamado *smart cast*).

```kotlin
        val next = if (every != null && unit != null) {
            calculateNextDue(now, every, unit)
        } else {
            null
        }
```
- Se o item tem repetição **completa** (número e unidade), calcula o próximo vencimento **a partir de `now`**. É a regra 5.
- Senão, `next` é `null`, e o item não tem novo vencimento (regra 6).
- Aqui `if` é uma **expressão**: ele devolve um valor que vai para `next`.

```kotlin
        return item.copy(lastDoneAt = now, nextDueAt = next)
```
- `copy()` cria uma cópia do item trocando só esses dois campos: `lastDoneAt` vira "agora" e `nextDueAt` vira o novo vencimento.
- O item original **não é alterado** (ele é imutável). Isso se chama **função pura**: mesma entrada, mesma saída, sem efeitos colaterais.
- Quem **salva** no banco é o ViewModel, via `repository.updateItem(...)`. Essa lógica fica de fora de propósito, para que a regra continue testável.

---

### 5.8 `TimeFormatter.kt`: textos "há X / vence em Y"

Transforma milissegundos em frases legíveis.

```kotlin
fun since(past: Long, now: Long = System.currentTimeMillis()): String {
    val days = Duration.between(Instant.ofEpochMilli(past), Instant.ofEpochMilli(now)).toDays()
```
- Calcula quantos **dias inteiros** se passaram entre `past` e `now`.
- `Duration.between(...)` mede o intervalo e `.toDays()` converte para dias, **descartando a parte fracionária**.

```kotlin
    return when {
        days < 1 -> "hoje"
        days == 1L -> "há 1 dia"
        days < 14 -> "há $days dias"
        days < 60 -> "há ${days / 7} semanas"
        days < 730 -> "há ${days / 30} meses"
        else -> "há ${days / 365} anos"
    }
```
- `when` sem argumento avalia as condições **de cima para baixo** e usa a primeira verdadeira.
- `days == 1L`: o `L` indica que o número é `Long`, para combinar com o tipo de `days`.
- `"há $days dias"`: o `$` insere o valor da variável no texto. Com expressões, usa-se `${...}`.
- Os limites escolhidos: até 13 dias mostra dias, até 59 mostra semanas, até 729 mostra meses, e depois anos.
- A divisão de inteiros descarta o resto: 20 dias ÷ 7 = 2 semanas.

```kotlin
fun until(due: Long, now: Long = System.currentTimeMillis()): String {
    val days = Duration.between(Instant.ofEpochMilli(now), Instant.ofEpochMilli(due)).toDays()
    return when {
        days < 0 -> "atrasado ${since(due, now)}"
        days == 0L -> "vence hoje"
        days == 1L -> "vence amanhã"
        days < 14 -> "vence em $days dias"
        ...
    }
}
```
- Mesma ideia, mas olhando para o **futuro**: o intervalo vai de `now` até `due`.
- `days < 0`: o vencimento já passou. Reaproveita `since()` para montar "atrasado há 2 dias".
- `days == 0L` → "vence hoje". `days == 1L` → "vence amanhã".

---

### 5.9 `RecurrenceLogicTest.kt`: os testes

Fica na pasta `test` (e não em `main`), porque o JUnit só existe lá. Roda no seu computador, sem emulador.

```kotlin
class RecurrenceLogicTest {
    @Test
    fun `concluir item recorrente define proximo vencimento a partir de agora`() {
```
- `@Test` marca a função como teste.
- Nomes entre crases permitem frases com espaços, e deixam o relatório de testes legível.

```kotlin
        val item = Item(title = "Trocar lençóis", type = ItemType.TASK,
                        repeatEvery = 2, repeatUnit = RepeatUnit.WEEKS)
        val now = 1_700_000_000_000L
```
- **Arrange (preparar)**: monta um item que repete a cada 2 semanas e fixa o "agora" em um valor conhecido. Os `_` apenas facilitam a leitura do número.

```kotlin
        val done = RecurrenceLogic.complete(item, now)
```
- **Act (agir)**: executa a função que queremos testar.

```kotlin
        assertEquals(now, done.lastDoneAt)
        assertNotNull(done.nextDueAt)
        assertTrue(done.nextDueAt!! > now)
```
- **Assert (verificar)**:
  - O `lastDoneAt` deve ser exatamente o `now`.
  - O `nextDueAt` não pode ser `null`.
  - O `nextDueAt` deve estar no futuro (`> now`).
- `!!` diz ao Kotlin "tenho certeza de que não é `null`". Aqui é seguro, porque a linha anterior já verificou.

O segundo teste (`concluir item sem repeticao nao gera proximo vencimento`) confirma a regra 6: um item sem repetição, depois de concluído, fica com `nextDueAt == null`.

---

## 6. Fluxo completo: concluindo "Trocar lençóis"

Como as peças trabalharão juntas quando a interface existir:

1. O usuário toca em "concluir" no item (**View**).
2. A View avisa o **ViewModel**.
3. O ViewModel chama `RecurrenceLogic.complete(item)` e recebe um item novo com `lastDoneAt = agora` e `nextDueAt = agora + 2 semanas`.
4. O ViewModel chama `repository.updateItem(itemNovo)`.
5. O **Repository** repassa para `itemDao.update(itemNovo)`.
6. O **Room** grava no SQLite.
7. O `Flow` de `observeByRelevance` percebe a mudança e **emite a lista nova**.
8. A View recebe a lista e atualiza o RecyclerView. Usando o `TimeFormatter`, o item agora mostra "feito hoje" e "vence em 2 semanas".

Os passos 1, 2 e 4 dependem do Passo 4 do projeto. Os demais já existem.

---

## 7. Glossário rápido

| Termo | Significado |
|---|---|
| **Entity** | Classe que vira tabela no banco |
| **DAO** | Interface que descreve as operações no banco |
| **Repository** | Camada que centraliza o acesso aos dados |
| **ViewModel** | Guarda o estado da tela e sobrevive a rotações |
| **Flow** | Fluxo de dados que emite novos valores quando algo muda |
| **suspend** | Função que roda em coroutine, fora da thread da tela |
| **Singleton** | Padrão em que só existe uma instância de algo |
| **KSP** | Ferramenta que gera código a partir de anotações (é como o Room escreve os DAOs) |
| **Foreign key** | Coluna que aponta para a linha de outra tabela |
| **Smart cast** | O Kotlin entende que uma variável não é `null` depois de checada |
| **Função pura** | Função sem efeitos colaterais: mesma entrada, mesma saída |
| **Epoch millis** | Data como número de milissegundos desde 01/01/1970 |

---

## 8. Limitações conhecidas e próximos passos

**Limitações atuais (propositais, para o MVP):**
- `toDays()` conta períodos de 24h, então "atrasado há 1 dia" pode variar por algumas horas. Uma precisão por dia de calendário fica para depois.
- A busca `LIKE` do SQLite não ignora acentos (buscar "lencol" não acha "lençol").
- Só existe a ordenação por relevância. Recentes, antigos e alfabético entram no Passo 5.
- O `Repository` ainda não tem a função "mover item entre categorias". Ela entra junto com o CRUD de categorias.

**Próximos passos:**
- **Passo 4**: ViewModel, Fragment da Home e RecyclerView com "feito há X / vence em Y".
- **Passo 5**: criar, editar, excluir e concluir itens pela interface.
- Depois: categorias, anotações, busca e ordenação, polimento, README e release 1.0.0.
