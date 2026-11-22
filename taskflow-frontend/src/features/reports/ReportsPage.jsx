import { useEffect, useRef, useState } from 'react';
import { Download, Info, RefreshCw, Search } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardHeader } from '../../components/common/Card';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { EmptyState, ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';
import { useToast } from '../../hooks/useToast';
import { buildReportRequest, parseReportError, reportService } from '../../services/reportService';
import {
  downloadBinaryResponse,
  isSavePickerSupported,
  saveReportFile,
} from '../../utils/download';
import { parseApiError } from '../../utils/errorHandler';
import { ReportFilters, createDefaultFilters, validateFilters } from './ReportFilters';
import { getReportMeta, REPORT_CATEGORIES } from './reportConstants';

const CARD_GRID = 'grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3';

function ReportCard({ report, formats, selected, onSelect }) {
  const meta = getReportMeta(report.id);
  const Icon = meta.icon;
  const category = REPORT_CATEGORIES[meta.category] || REPORT_CATEGORIES.overview;
  const supportedFormats = Array.isArray(report.formats) ? report.formats : [];

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={`group w-full rounded-xl text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40 ${
        selected ? 'ring-2 ring-brand-500' : ''
      }`}
    >
      <Card interactive className="relative flex h-full flex-col overflow-hidden">
        <span className="absolute inset-x-0 top-0 h-0.5 bg-brand-500 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true" />
        <CardBody className="flex flex-1 flex-col">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-brand-100 bg-brand-50 text-brand-600">
            <Icon className="h-5 w-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-sm font-semibold tracking-tight text-ink">
                {report.label || report.id}
              </h2>
              <Badge tone={category.tone}>{category.label}</Badge>
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-ink-muted">{meta.description}</p>
          </div>
        </div>

        <div className="mt-auto space-y-3 border-t border-line pt-3.5">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="mr-1 text-[11px] font-medium text-ink-faint">Formats</span>
            {supportedFormats.length > 0 ? (
              supportedFormats.map((format) => {
                const formatMeta = formats.find((entry) => entry.id === format);
                return (
                  <Badge key={format} tone="neutral" mono>
                    {formatMeta?.label || format}
                  </Badge>
                );
              })
            ) : (
              <span className="text-[11px] text-ink-faint">No formats listed by the service</span>
            )}
          </div>
          {report.requiresSprintId && (
            <p className="rounded-md border border-amber-200 bg-amber-50 px-2.5 py-2 text-[11px] leading-relaxed text-amber-700">
              Requires a valid sprint selection when the report is generated.
            </p>
          )}
          <p className={`text-[11px] font-medium ${selected ? 'text-brand-700' : 'text-ink-faint'}`}>
            {selected ? 'Selected — configure below' : 'Select to configure'}
          </p>
        </div>
        </CardBody>
      </Card>
    </button>
  );
}

export function ReportsPage() {
  const toast = useToast();
  const [catalog, setCatalog] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshToken, setRefreshToken] = useState(0);
  // Client-side catalog browsing only — never sent to the reports API.
  const [catalogSearch, setCatalogSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [selectedReportId, setSelectedReportId] = useState(null);
  const [filters, setFilters] = useState(createDefaultFilters);
  const [selectedFormat, setSelectedFormat] = useState('PDF');
  const [generating, setGenerating] = useState(false);
  const [downloadError, setDownloadError] = useState(null);
  const [apiFieldErrors, setApiFieldErrors] = useState({});
  const [lastDownload, setLastDownload] = useState(null);
  const [saving, setSaving] = useState(false);
  const generatingRef = useRef(false);
  const savingRef = useRef(false);
  const lastDownloadRef = useRef(null);
  const fallbackCleanupRef = useRef(null);

  useEffect(() => () => {
    lastDownloadRef.current?.cleanup?.();
    fallbackCleanupRef.current?.();
  }, []);

  useEffect(() => {
    let active = true;

    async function loadCatalog() {
      setLoading(true);
      setError(null);
      try {
        const data = await reportService.getCatalog();
        if (active) setCatalog(data);
      } catch (err) {
        const parsed = parseApiError(err);
        if (active) {
          setError(parsed.message);
          toast.error(parsed.message, 'Unable to load reports');
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    loadCatalog();
    return () => {
      active = false;
    };
    // refreshToken is the explicit retry trigger for this page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshToken]);

  const reportTypes = Array.isArray(catalog?.reportTypes) ? catalog.reportTypes : [];
  const formats = Array.isArray(catalog?.formats) ? catalog.formats : [];
  const visibleReports = reportTypes.filter((report) => {
    const meta = getReportMeta(report.id);
    if (categoryFilter !== 'ALL' && meta.category !== categoryFilter) return false;
    const term = catalogSearch.trim().toLowerCase();
    if (!term) return true;
    return [report.id, report.label, meta.description]
      .some((value) => String(value || '').toLowerCase().includes(term));
  });
  const groupedReports = Object.keys(REPORT_CATEGORIES)
    .map((category) => ({
      category,
      reports: visibleReports.filter((report) => getReportMeta(report.id).category === category),
    }))
    .filter((group) => group.reports.length > 0);
  const selectedReport = reportTypes.find((report) => report.id === selectedReportId) || null;
  const supportedFormats = Array.isArray(selectedReport?.formats) ? selectedReport.formats : [];
  const validationErrors = selectedReport
    ? { ...validateFilters(filters, selectedReport.id), ...apiFieldErrors }
    : {};
  const formatOptions = supportedFormats.map((format) => {
    const entry = formats.find((item) => item.id === format);
    return { value: format, label: entry?.label || format };
  });

  useEffect(() => {
    if (!selectedReport || supportedFormats.length === 0) return;
    setSelectedFormat((current) => (
      supportedFormats.includes(current) ? current : (supportedFormats[0] || '')
    ));
  }, [selectedReport, supportedFormats]);

  const handleSelectReport = (reportId) => {
    fallbackCleanupRef.current?.();
    fallbackCleanupRef.current = null;
    lastDownloadRef.current?.cleanup?.();
    lastDownloadRef.current = null;
    setLastDownload(null);
    setSelectedReportId(reportId);
    setDownloadError(null);
    setApiFieldErrors({});
    if (reportId !== 'SPRINT_BURNOWN') {
      setFilters((current) => (current.sprintId == null ? current : { ...current, sprintId: null }));
    }
  };

  const handleFiltersChange = (nextFilters) => {
    fallbackCleanupRef.current?.();
    fallbackCleanupRef.current = null;
    lastDownloadRef.current?.cleanup?.();
    lastDownloadRef.current = null;
    setLastDownload(null);
    setFilters(nextFilters);
    setApiFieldErrors({});
    setDownloadError(null);
  };

  const handleGenerate = async (format) => {
    if (!selectedReport || generatingRef.current) return;
    if (!supportedFormats.includes(format)) return;
    if (Object.keys(validateFilters(filters, selectedReport.id)).length > 0) return;

    generatingRef.current = true;
    setSelectedFormat(format);
    setGenerating(true);
    setDownloadError(null);
    setApiFieldErrors({});

    try {
      const request = buildReportRequest({ type: selectedReport.id, format, filters });
      const response = format === 'PDF'
        ? await reportService.generatePdf(request)
        : await reportService.generateXlsx(request);
      const result = await downloadBinaryResponse(response, {
        fallbackName: selectedReport.id.toLowerCase(),
        extension: format === 'PDF' ? 'pdf' : 'xlsx',
      });
      lastDownloadRef.current?.cleanup?.();
      lastDownloadRef.current = result;
      setLastDownload({ ...result, format });
      toast.success(
        isSavePickerSupported()
          ? 'Report ready. Choose where to save it.'
          : 'Report ready. Use Save to computer to download it.',
        'Report ready'
      );
    } catch (err) {
      setLastDownload(null);
      const parsed = await parseReportError(err);
      setApiFieldErrors(parsed.fieldErrors || {});
      setDownloadError(parsed.message);
      toast.error(parsed.message, 'Report generation failed');
    } finally {
      generatingRef.current = false;
      setGenerating(false);
    }
  };

  const clearLastDownload = () => {
    lastDownloadRef.current?.cleanup?.();
    lastDownloadRef.current = null;
    setLastDownload(null);
  };

  const handleSave = async () => {
    if (!lastDownload || savingRef.current) return;

    savingRef.current = true;
    setSaving(true);
    // Retire the previous fallback URL only once a new save attempt begins.
    fallbackCleanupRef.current?.();
    fallbackCleanupRef.current = null;
    try {
      const outcome = await saveReportFile(lastDownload);
      if (outcome.cancelled) {
        toast.info('Save cancelled.', 'Save cancelled');
        return;
      }

      if (outcome.cleanup) fallbackCleanupRef.current = outcome.cleanup;

      if (outcome.saved) {
        toast.success('Report saved successfully.', 'Report saved');
      } else if (outcome.fallback) {
        toast.warning(
          `A normal download was started for ${outcome.filename}. The file will be in your browser's download location, not necessarily the folder selected in the save dialog.`,
          'Download started'
        );
      } else {
        toast.success(
          `Standard download started for ${outcome.filename}. Check your browser downloads.`,
          'Report download'
        );
      }
      clearLastDownload();
    } catch (err) {
      toast.error(err?.message || 'The report could not be saved.', 'Save failed');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reports & Analytics"
        description="Generate downloadable PDF and Excel reports from authorized workspace data."
        meta={
          <Badge tone="brand" mono>
            {reportTypes.length > 0 ? `${reportTypes.length} reports` : 'Reports'}
          </Badge>
        }
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setRefreshToken((value) => value + 1)}
            loading={loading}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Refresh catalog
          </Button>
        }
      />

      <Card>
        <CardBody className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-brand-100 bg-brand-50 text-brand-600">
            <Info className="h-4 w-4" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium text-ink">Document reports, not live charts</p>
            <p className="mt-1 max-w-3xl text-xs leading-relaxed text-ink-muted">
              This module creates downloadable PDF and Excel files from the data you are authorized
              to view. It does not provide interactive analytics charts or live KPI dashboards.
            </p>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <Input
            aria-label="Search reports"
            value={catalogSearch}
            onChange={(event) => setCatalogSearch(event.target.value)}
            placeholder="Search report names or descriptions…"
            leftIcon={<Search className="h-4 w-4" />}
            className="flex-1"
          />
          <div className="w-full sm:w-52">
            <Select
              aria-label="Filter reports by category"
              value={categoryFilter}
              onChange={(event) => setCategoryFilter(event.target.value)}
              options={[
                { value: 'ALL', label: 'All categories' },
                ...Object.entries(REPORT_CATEGORIES).map(([id, meta]) => ({
                  value: id,
                  label: meta.label,
                })),
              ]}
            />
          </div>
        </CardBody>
      </Card>

      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-base font-semibold tracking-tight text-ink">Available reports</h2>
          <p className="mt-1 text-xs text-ink-muted">
            Report types and formats are provided by the reporting service.
          </p>
        </div>
      </div>

      {loading ? (
        <div className={CARD_GRID} aria-label="Loading report catalog">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={`report-skeleton-${index}`} className="h-48 w-full rounded-xl" />
          ))}
        </div>
      ) : error ? (
        <ErrorState
          title="Unable to load the report catalog"
          description={error}
          onRetry={() => setRefreshToken((value) => value + 1)}
          retrying={loading}
        />
      ) : reportTypes.length === 0 ? (
        <EmptyState
          icon={Info}
          title="No reports available"
          description="The reporting service did not return any report types. Try refreshing the catalog."
          action={
            <Button variant="secondary" size="sm" onClick={() => setRefreshToken((value) => value + 1)}>
              Refresh catalog
            </Button>
          }
        />
      ) : visibleReports.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No reports match your search"
          description="Try a different search term or category to see the available report types."
          action={
            <Button
              variant="secondary"
              size="sm"
              onClick={() => { setCatalogSearch(''); setCategoryFilter('ALL'); }}
            >
              Clear catalog filters
            </Button>
          }
        />
      ) : (
        <div className="space-y-6">
          {groupedReports.map(({ category, reports }) => {
            const meta = REPORT_CATEGORIES[category];
            return (
              <section key={category} className="space-y-3">
                <div className="flex items-center gap-2.5">
                  <h3 className="text-sm font-semibold text-ink">{meta.label}</h3>
                  <Badge tone={meta.tone} mono>{reports.length}</Badge>
                  <span className="h-px flex-1 bg-line" aria-hidden="true" />
                </div>
                <div className={CARD_GRID}>
                  {reports.map((report) => (
                    <ReportCard
                      key={report.id}
                      report={report}
                      formats={formats}
                      selected={selectedReportId === report.id}
                      onSelect={() => handleSelectReport(report.id)}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      )}

      {!loading && !error && selectedReport && (
        <div className="space-y-4 border-t border-line pt-6">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-3">
              {(() => {
                const SelectedIcon = getReportMeta(selectedReport.id).icon;
                return (
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-brand-100 bg-brand-50 text-brand-600">
                    <SelectedIcon className="h-5 w-5" aria-hidden="true" />
                  </div>
                );
              })()}
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-lg font-semibold tracking-tight text-ink">
                    {selectedReport.label || selectedReport.id}
                  </h2>
                  <Badge tone="brand" mono>{selectedReport.id}</Badge>
                </div>
                <p className="mt-1 max-w-2xl text-xs leading-relaxed text-ink-muted">
                  {getReportMeta(selectedReport.id).description}
                </p>
              </div>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setSelectedReportId(null)}>
              Change report
            </Button>
          </div>

          {Object.keys(validationErrors).length > 0 && (
            <p className="rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-700">
              Review {Object.keys(validationErrors).length} filter issue
              {Object.keys(validationErrors).length === 1 ? '' : 's'} before generating a report.
            </p>
          )}

          <ReportFilters
            reportType={selectedReport.id}
            value={filters}
            onChange={handleFiltersChange}
            errors={validationErrors}
          />

          <Card>
            <CardHeader
              title="Generate report"
              description="The file is created from the filters above and downloaded to your device."
            />
            <CardBody className="space-y-4">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 sm:items-end">
                <Select
                  id="report-format"
                  label="Format"
                  value={supportedFormats.includes(selectedFormat) ? selectedFormat : (formatOptions[0]?.value || '')}
                  onChange={(event) => setSelectedFormat(event.target.value)}
                  options={formatOptions}
                  disabled={generating}
                />
                <div className="flex flex-wrap gap-2 sm:col-span-2">
                  {supportedFormats.includes('PDF') && (
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={() => handleGenerate('PDF')}
                      loading={generating && selectedFormat === 'PDF'}
                      disabled={generating || Object.keys(validationErrors).length > 0}
                      leftIcon={<Download className="h-3.5 w-3.5" />}
                    >
                      Generate PDF
                    </Button>
                  )}
                  {supportedFormats.includes('XLSX') && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleGenerate('XLSX')}
                      loading={generating && selectedFormat === 'XLSX'}
                      disabled={generating || Object.keys(validationErrors).length > 0}
                      leftIcon={<Download className="h-3.5 w-3.5" />}
                    >
                      Generate Excel
                    </Button>
                  )}
                </div>
              </div>

              {downloadError && (
                <div
                  role="alert"
                  className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
                >
                  {downloadError}
                </div>
              )}

              {lastDownload && (
                <div className="rounded-lg border border-brand-100 bg-brand-50/60 px-3.5 py-3">
                  <p className="text-xs text-ink">
                    Generated file:{' '}
                    <span className="font-mono text-[11px]">{lastDownload.filename}</span>
                  </p>
                  <p className="mt-1 text-[11px] leading-relaxed text-ink-muted">
                    {isSavePickerSupported()
                      ? 'Choose where to save this report on your computer.'
                      : 'Your browser does not support a file picker. The standard download will let you choose a destination in your browser settings.'}
                  </p>
                  <div className="mt-2.5 flex flex-wrap gap-2">
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={handleSave}
                      loading={saving}
                      disabled={saving}
                      leftIcon={<Download className="h-3.5 w-3.5" />}
                    >
                      Save to computer
                    </Button>
                    <Button variant="ghost" size="sm" onClick={clearLastDownload} disabled={saving}>
                      Dismiss
                    </Button>
                  </div>
                </div>
              )}

              {supportedFormats.length === 0 && (
                <p className="text-xs text-ink-faint">
                  The catalog did not list any formats for this report.
                </p>
              )}
            </CardBody>
          </Card>
        </div>
      )}

      {!loading && !error && !selectedReport && reportTypes.length > 0 && (
        <EmptyState
          icon={Info}
          title="Select a report to configure filters"
          description="Choose one of the report types above to set the period, scope and granularity."
        />
      )}
    </div>
  );
}
