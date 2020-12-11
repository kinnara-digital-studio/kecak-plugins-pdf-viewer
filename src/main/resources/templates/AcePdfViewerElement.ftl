<style type="text/css">
.pnx-icon-pdf-viewer {
    width: 24px !important;
    height: 24px !important;
}
</style>
<div class="form-cell" ${elementMetaData!}>
    <#if includeMetaData!>
      <label class="label">${element.properties.label}</label>
        <img class="pnx-icon-pdf-viewer" src="${request.contextPath}/plugin/${className}/images/pdf-logo.png" />
      <span class="form-floating-label">PDF VIEWER</span>
    <#else>
        <div class="row">
            <div class="col-md-12">
                <div class="embed-responsive embed-responsive-${ratio}">
                  <iframe class="embed-responsive-item" src="${src}" type="application/pdf"></iframe>
                </div>
            </div>
        </div>
    </#if>
</div>