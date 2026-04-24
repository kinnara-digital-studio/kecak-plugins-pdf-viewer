<style>
    #drop-zone-${elementParamName!} {
        border: 2px dashed #007bff;
        border-radius: 5px;
        padding: 30px;
        text-align: center;
        background: #f8f9fa;
        cursor: pointer;
        transition: background 0.2s;
    }

    #drop-zone-${elementParamName!}:hover,
    #drop-zone-${elementParamName!}.drag-over {
        background: #e3f0ff;
        border-color: #0056b3;
    }

    #full-page-preview-${elementParamName!} {
        display: none;
        position: fixed;
        top: 0; left: 0;
        width: 100vw; height: 100vh;
        background: rgba(0,0,0,0.85);
        z-index: 9999;
        flex-direction: column;
        align-items: center;
        justify-content: center;
    }

    #full-page-preview-${elementParamName!} iframe {
        width: 90%;
        height: 85%;
        border: none;
        background: white;
    }

    #close-btn-${elementParamName!} {
        position: absolute;
        top: 20px; right: 40px;
        color: white;
        font-size: 30px;
        cursor: pointer;
        font-family: Arial, sans-serif;
        user-select: none;
    }

    #pdf-loader-${elementParamName!} {
        color: white;
        text-align: center;
        font-size: 16px;
        margin-bottom: 12px;
    }

    #progress-wrap-${elementParamName!} {
        display: none;
        width: 60%;
        margin-top: 10px;
    }

    #progress-wrap-${elementParamName!} .progress-track {
        background: #444;
        border-radius: 4px;
        height: 8px;
        width: 100%;
    }

    #progress-bar-${elementParamName!} {
        background: #007bff;
        border-radius: 4px;
        height: 8px;
        width: 0%;
        transition: width 0.3s;
    }

    #progress-text-${elementParamName!} {
        color: #ccc;
        font-size: 13px;
        margin-top: 6px;
        text-align: center;
    }

    ul.form-fileupload-value li {
        display: block;
        margin-top: 8px;
    }

    .pdf-preview-link {
        margin-left: 8px;
        font-size: smaller;
        color: #007bff;
        cursor: pointer;
        text-decoration: underline;
    }
</style>

<div class="form-cell" ${elementMetaData!}>
    <label class="label">
        ${element.properties.label!}
        <span class="form-cell-validator">${decoration}</span>
        <#if error??>
            <span class="form-error-message">${error}</span>
        </#if>
    </label>

    <div class="form-fileupload">

        <#if element.properties.readonly! != 'true'>
            <!-- Drop Zone -->
            <div id="drop-zone-${elementParamName!}">
                <p style="margin:0; color:#555;">
                    Drag & Drop PDF di sini atau <strong>klik untuk pilih file</strong>
                </p>
                <small style="color:#999;">Hanya file PDF</small>
                <input id="input-${elementParamName!}"
                       name="${elementParamName!}"
                       type="file"
                       accept="application/pdf"
                       size="${element.properties.size!}"
                       <#if error??>class="form-error-cell"</#if>
                       <#if element.properties.multiple! == 'true'>multiple</#if>
                       style="display:none;"/>
            </div>
        </#if>
        <#if tempFilePaths?? || filePaths??>
                <style>
                    ul.form-fileupload-value li{display:block;}
                </style>
                <ul class="form-fileupload-value">
                    <#if tempFilePaths??>
                        <#list tempFilePaths?keys as key>
                            <li>
                                ${tempFilePaths[key]!?html}
                                <input type="hidden" name="${elementParamName!}_path" value="${key!?html}"/>
                                <#if element.properties.readonly! != 'true'>
                                    <input type="checkbox" name="${elementParamName!}_remove" value="${key!?html}" /> <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                                </#if>
                            </li>
                        </#list>
                    </#if>
                    <#if filePaths??>
                        <#list filePaths?keys as key>
                            <li>
                                <a href="${request.contextPath}${key!?html}" target="_blank" >${filePaths[key]!?html}</a>
                                <input type="hidden" name="${elementParamName!}_path" value="${filePaths[key]!?html}"/>
                                <#if element.properties.readonly! != 'true'>
                                    <input type="checkbox" name="${elementParamName!}_remove" value="${filePaths[key]!?html}" /> <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                                </#if>
                            </li>
                        </#list>
                    </#if>
                </ul>
            </#if>

        <!-- Full Page Preview Overlay -->
        <div id="full-page-preview-${elementParamName!}">
            <span id="close-btn-${elementParamName!}">&times; Tutup Preview</span>
            <div id="pdf-loader-${elementParamName!}">Memuat PDF...</div>
            <div id="progress-wrap-${elementParamName!}">
                <div class="progress-track">
                    <div id="progress-bar-${elementParamName!}"></div>
                </div>
                <div id="progress-text-${elementParamName!}">Uploading... 0%</div>
            </div>
            <iframe id="full-viewer-${elementParamName!}" style="display:none;"></iframe>
        </div>

        <!-- List File -->
        <#if tempFilePaths?? || filePaths??>
            <ul class="form-fileupload-value">
                <#if tempFilePaths??>
                    <#list tempFilePaths?keys as key>
                        <li>
                            📄 ${tempFilePaths[key]!?html}
                            <input type="hidden" name="${elementParamName!}_path" value="${key!?html}"/>
                            <span class="pdf-preview-link"
                                  data-path="${key!?html}"
                                  onclick="previewTempPdf('${elementParamName!}', '${key!?html}')">
                                Preview
                            </span>
                            <#if element.properties.readonly! != 'true'>
                                <input type="checkbox" name="${elementParamName!}_remove" value="${key!?html}"/>
                                <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                            </#if>
                        </li>
                    </#list>
                </#if>
                <#if filePaths??>
                    <#list filePaths?keys as key>
                        <li>
                            📄 <a href="${request.contextPath}${key!?html}" target="_blank">
                                ${filePaths[key]!?html}
                            </a>
                            <span class="pdf-preview-link"
                                  onclick="previewSavedPdf('${elementParamName!}', '${request.contextPath}${key!?html}')">
                                Preview
                            </span>
                            <input type="hidden" name="${elementParamName!}_path" value="${filePaths[key]!?html}"/>
                            <#if element.properties.readonly! != 'true'>
                                <input type="checkbox" name="${elementParamName!}_remove" value="${filePaths[key]!?html}"/>
                                <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                            </#if>
                        </li>
                    </#list>
                </#if>
            </ul>
        </#if>

    </div>
</div>

<script type="text/javascript">
(function() {
    const paramName  = "${elementParamName!}";
    const dropZone   = document.getElementById('drop-zone-'         + paramName);
    const fileInput  = document.getElementById('input-'             + paramName);
    const overlay    = document.getElementById('full-page-preview-' + paramName);
    const viewer     = document.getElementById('full-viewer-'       + paramName);
    const loader     = document.getElementById('pdf-loader-'        + paramName);
    const progressWrap = document.getElementById('progress-wrap-'   + paramName);
    const progressBar  = document.getElementById('progress-bar-'    + paramName);
    const progressText = document.getElementById('progress-text-'   + paramName);
    const closeBtn   = document.getElementById('close-btn-'         + paramName);

    if (!dropZone) return;

    // ── Drag & Drop ──────────────────────────────────────────────
    dropZone.addEventListener('click', () => fileInput.click());

    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragleave', () => {
        dropZone.classList.remove('drag-over');
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
        const files = e.dataTransfer.files;
        if (files.length > 0) handleFile(files[0]);
    });

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) handleFile(e.target.files[0]);
    });

    // ── Handle File ───────────────────────────────────────────────
    function handleFile(file) {
        // Validasi tipe file
        if (file.type !== 'application/pdf') {
            alert('Hanya file PDF yang diperbolehkan.');
            return;
        }

        // Validasi ukuran jika ada maxSize
        <#if element.properties.maxSize?? && element.properties.maxSize != ''>
        const maxSizeMB = ${element.properties.maxSize};
        const fileSizeMB = file.size / (1024 * 1024);
        if (fileSizeMB > maxSizeMB) {
            alert('${element.properties.maxSizeMsg!'File terlalu besar'} (Maks: ' + maxSizeMB + ' MB)');
            return;
        }
        </#if>

        // Tampilkan overlay + upload
        showOverlay('Mengupload PDF...');
        uploadFile(file);
    }

    // ── Upload via XHR ────────────────────────────────────────────
    function uploadFile(file) {
        const formData = new FormData();
        formData.append('pdfFile', file);

        const csrfToken = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenValue : '';
        const csrfName  = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenName  : '';

        const xhr = new XMLHttpRequest();

        // Progress upload
        xhr.upload.addEventListener('progress', (e) => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                progressBar.style.width = pct + '%';
                progressText.textContent = 'Uploading... ' + pct + '%';
            }
        });

        xhr.upload.addEventListener('load', () => {
            loader.textContent = 'Memproses PDF di server...';
            progressWrap.style.display = 'none';
        });

        xhr.onload = function() {
            if (xhr.status === 200) {
                const blob = new Blob([xhr.response], { type: 'application/pdf' });
                if (blob.size === 0) {
                    alert('Server mengembalikan file kosong.');
                    hideOverlay();
                    return;
                }
                showPdf(URL.createObjectURL(blob));
            } else {
                // Decode error message dari arraybuffer
                const errText = new TextDecoder().decode(new Uint8Array(xhr.response));
                alert('Error ' + xhr.status + ': ' + errText);
                hideOverlay();
            }
        };

        xhr.onerror = function() {
            alert('Network error. Periksa koneksi atau server.');
            hideOverlay();
        };

        xhr.open('POST', '${request.contextPath}/web/json/plugin/${className!}/service');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        if (csrfName && csrfToken) {
            xhr.setRequestHeader(csrfName, csrfToken);
        }
        xhr.responseType = 'arraybuffer';
        xhr.send(formData);
    }

    // ── UI Helpers ────────────────────────────────────────────────
    function showOverlay(loaderText) {
        overlay.style.display = 'flex';
        loader.style.display  = 'block';
        loader.textContent    = loaderText;
        progressWrap.style.display = 'block';
        progressBar.style.width    = '0%';
        progressText.textContent   = 'Uploading... 0%';
        viewer.style.display  = 'none';
        viewer.src = 'about:blank';
    }

    function hideOverlay() {
        overlay.style.display = 'none';
        viewer.src = '';
    }

    function showPdf(url) {
        loader.style.display = 'none';
        viewer.style.display = 'block';
        viewer.src = url;
    }

    // Tutup overlay
    closeBtn.addEventListener('click', () => {
        if (viewer.src.startsWith('blob:')) URL.revokeObjectURL(viewer.src);
        hideOverlay();
    });

    // ── Preview Functions (dipanggil dari onclick di list file) ───
    window.previewTempPdf = function(pName, path) {
        if (pName !== paramName) return;
        const url = '${request.contextPath}/web/json/plugin/${element.properties.className!}/service?_path=' + encodeURIComponent(path);
        showOverlay('Memuat preview...');
        showPdf(url);
    };

    window.previewSavedPdf = function(pName, url) {
        if (pName !== paramName) return;
        showOverlay('Memuat preview...');
        showPdf(url);
    };

})();
</script>