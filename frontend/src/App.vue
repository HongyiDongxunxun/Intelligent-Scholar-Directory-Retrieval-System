<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BookOpen, Bot, Building2, ChevronRight, Database, FileSearch, LoaderCircle,
  Network, Search, Sparkles, UserRoundSearch, Users, X,
} from 'lucide-vue-next'
import { api } from './api'

const tabs = [
  { id: 'publications', label: '文献检索', icon: FileSearch },
  { id: 'scholars', label: '学者发现', icon: UserRoundSearch },
  { id: 'topic', label: '专题装配', icon: Network },
  { id: 'ai', label: '自然语言解析', icon: Bot },
]
const active = ref('publications')
const loading = ref(false)
const error = ref('')
const indexStatus = ref(null)
const entity = ref(null)

const publicationForm = reactive({ field: 'KEYWORD', value: '知识组织', yearStart: 2016, yearEnd: 2025, sort: 'RELEVANCE' })
const publicationResult = ref(null)
const scholarForm = reactive({ value: '数字人文', institution: '', strictInstitution: false })
const scholarResult = ref(null)
const topicForm = reactive({ topic: '知识组织', topSize: 10, includeGraph: true })
const topicResult = ref(null)
const aiForm = reactive({ mode: 'scholar', request: '寻找近十年研究数字人文的合成大学01学者' })
const aiDraft = ref(null)
const aiResult = ref(null)

const resultCount = computed(() => {
  if (active.value === 'publications') return publicationResult.value?.total ?? 0
  if (active.value === 'scholars') return scholarResult.value?.total ?? 0
  if (active.value === 'topic') return topicResult.value?.candidatePublicationCount ?? 0
  return aiResult.value?.total ?? 0
})

async function run(task) {
  loading.value = true
  error.value = ''
  try { await task() } catch (e) { error.value = e.message } finally { loading.value = false }
}

function filters(yearStart, yearEnd) {
  return { yearStart: yearStart || null, yearEnd: yearEnd || null, journalNames: [], disciplineIds: [] }
}

async function searchPublications() {
  await run(async () => {
    const payload = await api.post('/publications/search', {
      conditions: [{ operator: 'AND', field: publicationForm.field, value: publicationForm.value, match: 'FUZZY' }],
      filters: filters(publicationForm.yearStart, publicationForm.yearEnd),
      sort: publicationForm.sort,
      page: { size: 20, cursor: null },
    })
    publicationResult.value = payload.data
  })
}

async function searchScholars() {
  await run(async () => {
    const conditions = [{ operator: 'AND', field: 'KEYWORD', value: scholarForm.value, match: 'FUZZY' }]
    if (scholarForm.institution.trim()) conditions.push({ operator: 'AND', field: 'INSTITUTION', value: scholarForm.institution, match: 'FUZZY' })
    const payload = await api.post('/scholars/search', {
      conditions, filters: filters(), sort: 'RELEVANCE', page: { size: 20, cursor: null },
      strictInstitution: scholarForm.strictInstitution, includeCoauthors: true,
      scanBatchSize: 100, maxScanRounds: 5,
    })
    scholarResult.value = payload.data
  })
}

async function assembleTopic() {
  await run(async () => {
    const payload = await api.post('/topics/assemble', {
      topic: topicForm.topic,
      conditions: [{ operator: 'AND', field: 'KEYWORD', value: topicForm.topic, match: 'FUZZY' }],
      filters: filters(), candidateLimit: 500, topSize: Number(topicForm.topSize),
      assembleTypes: ['PUBLICATION', 'SCHOLAR', 'INSTITUTION', 'KEYWORD'],
      includeSubtopics: true, includeGraph: topicForm.includeGraph,
    })
    topicResult.value = payload.data
  })
}

async function parseAi() {
  await run(async () => {
    const payload = await api.post(`/ai/${aiForm.mode}-search/parse`, { request: aiForm.request, locale: 'zh-CN' })
    aiDraft.value = payload.data
    aiResult.value = null
  })
}

async function confirmAi() {
  if (!aiDraft.value?.draftQuery) return
  await run(async () => {
    const key = aiForm.mode === 'article' ? 'approvedQuery' : 'approvedQuery'
    const payload = await api.post(`/ai/${aiForm.mode}-search/confirm`, { [key]: aiDraft.value.draftQuery })
    aiResult.value = payload.data
  })
}

async function openEntity(type, id) {
  await run(async () => { entity.value = (await api.get(`/entities/${type}/${id}`)).data })
}

function scorePercent(score) { return `${Math.round((score || 0) * 100)}%` }

onMounted(async () => {
  try { indexStatus.value = (await api.get('/system/index-status')).data } catch { indexStatus.value = null }
  searchPublications()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand"><div class="brand-mark"><BookOpen :size="20" /></div><div><strong>交互式学者智能目录</strong><span>审稿验证重构版</span></div></div>
      <div class="system-state" :class="{ offline: !indexStatus }">
        <Database :size="16" />
        <span>{{ indexStatus ? `合成索引 ${indexStatus.indexDocumentCount} 条` : '后端未连接' }}</span>
      </div>
    </header>

    <div class="workspace">
      <aside class="sidebar" aria-label="主导航">
        <button v-for="tab in tabs" :key="tab.id" class="nav-item" :class="{ active: active === tab.id }" @click="active = tab.id">
          <component :is="tab.icon" :size="19" /><span>{{ tab.label }}</span>
        </button>
        <div class="data-note"><Database :size="18" /><strong>公开验证数据</strong><span>完全合成，不含原始实体与业务密钥</span></div>
      </aside>

      <main class="main">
        <div class="page-heading">
          <div><p>INTELLIGENT SCHOLAR DIRECTORY</p><h1>{{ tabs.find(t => t.id === active)?.label }}</h1></div>
          <div class="metric"><span>当前结果</span><strong>{{ resultCount }}</strong></div>
        </div>

        <div v-if="error" class="alert">{{ error }}</div>

        <section v-if="active === 'publications'" class="panel">
          <form class="query-grid" @submit.prevent="searchPublications">
            <label><span>检索字段</span><select v-model="publicationForm.field"><option value="KEYWORD">关键词</option><option value="TITLE">标题</option><option value="JOURNAL">期刊</option><option value="DISCIPLINE">学科</option></select></label>
            <label class="grow"><span>检索词</span><input v-model="publicationForm.value" required /></label>
            <label><span>起始年</span><input v-model.number="publicationForm.yearStart" type="number" min="2014" max="2025" /></label>
            <label><span>结束年</span><input v-model.number="publicationForm.yearEnd" type="number" min="2014" max="2025" /></label>
            <label><span>排序</span><select v-model="publicationForm.sort"><option value="RELEVANCE">相关度</option><option value="CITATIONS">被引量</option><option value="YEAR_DESC">年份</option></select></label>
            <button class="primary" type="submit" :disabled="loading"><Search :size="17" />检索</button>
          </form>
          <div class="result-list" v-if="publicationResult">
            <article v-for="item in publicationResult.items" :key="item.entity.id" class="result-row" @click="openEntity('PUBLICATION', item.entity.id)">
              <div class="year">{{ item.year }}</div><div class="result-main"><h2>{{ item.title }}</h2><p>{{ item.scholars.map(a => a.label).join('、') }} · {{ item.journal }}</p><div class="chips"><span v-for="tag in item.keywords.slice(0, 4)" :key="tag.id">{{ tag.label }}</span></div></div>
              <div class="citation"><strong>{{ item.citationCount }}</strong><span>被引</span></div><ChevronRight :size="18" />
            </article>
            <div v-if="!publicationResult.items.length" class="empty">没有匹配文献，请调整检索条件。</div>
          </div>
        </section>

        <section v-if="active === 'scholars'" class="panel">
          <form class="query-grid scholar-query" @submit.prevent="searchScholars">
            <label class="grow"><span>研究主题</span><input v-model="scholarForm.value" required /></label>
            <label class="grow"><span>机构（可选）</span><input v-model="scholarForm.institution" placeholder="合成大学01" /></label>
            <label class="check"><input v-model="scholarForm.strictInstitution" type="checkbox" /><span>严格机构归属</span></label>
            <button class="primary" type="submit" :disabled="loading"><Users :size="17" />发现学者</button>
          </form>
          <div class="scholar-grid" v-if="scholarResult">
            <article v-for="item in scholarResult.items" :key="item.entity.id" class="scholar-card" @click="openEntity('SCHOLAR', item.entity.id)">
              <div class="avatar">{{ item.entity.label.slice(-2) }}</div><div class="scholar-info"><h2>{{ item.entity.label }}</h2><p>{{ item.matchedPublicationCount }} 篇匹配文献 · {{ item.matchedCitationCount }} 次被引</p><div class="bar"><i :style="{ width: scorePercent(item.fieldScore) }"></i></div></div><strong class="score">{{ item.fieldScore.toFixed(3) }}</strong>
            </article>
          </div>
          <div v-if="scholarResult" class="scan-state">已扫描 {{ scholarResult.scan.scannedPublicationCount }} 篇候选文献 · {{ scholarResult.scan.complete ? '扫描完整' : '达到扫描上限' }}</div>
        </section>

        <section v-if="active === 'topic'" class="panel">
          <form class="query-grid topic-query" @submit.prevent="assembleTopic">
            <label class="grow"><span>专题名称</span><input v-model="topicForm.topic" required /></label>
            <label><span>每类 Top N</span><input v-model.number="topicForm.topSize" type="number" min="1" max="30" /></label>
            <label class="check"><input v-model="topicForm.includeGraph" type="checkbox" /><span>返回关系图</span></label>
            <button class="primary" type="submit" :disabled="loading"><Sparkles :size="17" />装配专题</button>
          </form>
          <template v-if="topicResult">
            <div class="topic-meta"><span>候选文献 {{ topicResult.candidatePublicationCount }}</span><span>缓存 {{ topicResult.cacheStatus }}</span><span>公式 0.70 Rel + 0.20 Str + 0.10 Inf</span></div>
            <div class="topic-columns">
              <div class="rank-column"><h2><FileSearch :size="17" />核心文献</h2><button v-for="item in topicResult.papers" :key="item.entity.id" @click="openEntity('PUBLICATION', item.entity.id)"><b>{{ item.rank }}</b><span>{{ item.entity.label }}</span><strong>{{ item.score.toFixed(3) }}</strong></button></div>
              <div class="rank-column"><h2><Users :size="17" />核心学者</h2><button v-for="item in topicResult.scholars" :key="item.entity.id" @click="openEntity('SCHOLAR', item.entity.id)"><b>{{ item.rank }}</b><span>{{ item.entity.label }}</span><strong>{{ item.score.toFixed(3) }}</strong></button></div>
              <div class="rank-column"><h2><Building2 :size="17" />核心机构</h2><button v-for="item in topicResult.institutions" :key="item.entity.id" @click="openEntity('INSTITUTION', item.entity.id)"><b>{{ item.rank }}</b><span>{{ item.entity.label }}</span><strong>{{ item.score.toFixed(3) }}</strong></button></div>
            </div>
          </template>
        </section>

        <section v-if="active === 'ai'" class="ai-layout">
          <div class="panel ai-input">
            <div class="segmented"><button :class="{ active: aiForm.mode === 'scholar' }" @click="aiForm.mode = 'scholar'">学者</button><button :class="{ active: aiForm.mode === 'article' }" @click="aiForm.mode = 'article'">文献</button></div>
            <label><span>自然语言需求</span><textarea v-model="aiForm.request" rows="6"></textarea></label>
            <button class="primary" :disabled="loading" @click="parseAi"><Bot :size="17" />生成可审阅条件</button>
          </div>
          <div class="panel draft-panel">
            <div class="section-title"><div><span>STRUCTURED DRAFT</span><h2>结构化草案</h2></div><button class="secondary" :disabled="!aiDraft || loading" @click="confirmAi"><Search :size="16" />确认执行</button></div>
            <template v-if="aiDraft"><div class="draft-row"><span>解析器版本</span><strong>{{ aiDraft.parserConfigVersion }}</strong></div><div v-for="(condition, i) in aiDraft.draftQuery.conditions" :key="i" class="condition"><b>{{ condition.operator }}</b><span>{{ condition.field }}</span><strong>{{ condition.value }}</strong><em>{{ condition.match }}</em></div><div v-if="aiDraft.warnings?.length" class="warning">{{ aiDraft.warnings.join('；') }}</div></template>
            <div v-else class="empty">解析结果将在确认执行前展示，用户可检查每个条件。</div>
            <div v-if="aiResult" class="ai-result"><strong>执行完成</strong><span>返回 {{ aiResult.total }} 条结果</span></div>
          </div>
        </section>

        <div v-if="loading" class="loading"><LoaderCircle :size="22" />正在处理请求</div>
      </main>
    </div>

    <div v-if="entity" class="drawer-backdrop" @click.self="entity = null">
      <aside class="drawer">
        <button class="icon-button close" title="关闭" @click="entity = null"><X :size="20" /></button>
        <p class="eyebrow">ENTITY EVIDENCE</p><h2>{{ entity.entity.label }}</h2><div class="entity-type">{{ entity.entity.type }} · {{ entity.entity.id }}</div>
        <h3>属性</h3><pre>{{ JSON.stringify(entity.attributes, null, 2) }}</pre><h3>关联证据</h3><div class="relation-count">{{ Array.isArray(entity.relations) ? entity.relations.length : 1 }} 项关联记录</div>
      </aside>
    </div>
  </div>
</template>
