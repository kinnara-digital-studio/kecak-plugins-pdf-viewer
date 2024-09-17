<style type="text/css">
.pnx-icon-pdf-viewer {
    width: 24px !important;
    height: 24px !important;
}
</style>

<#--<div class="form-cell" ${elementMetaData!}>
    <label class="label">${menu.properties.label}</label>
    <br>
    <embed src="${src!}" width="${menu.properties.width!320}" height="${menu.properties.height!320}" type="application/pdf">
</div>-->

<div class="form-cell" ${elementMetaData!}>
      <label class="label">${label!}</label>
        <img class="pnx-icon-pdf-viewer" src="${request.contextPath}/plugin/${className}/images/pdf-logo.png" />
      <span class="form-floating-label">DOCUMENT VIEWER</span>
        <div class="row">
            <div class="col-md-12">
                <div class="embed-responsive embed-responsive-${ratio}">
                  <#--  <iframe src="https://docs.google.com/gview?url=${url}&embedded=true"></iframe>  -->
                  <iframe src="https://view.officeapps.live.com/op/embed.aspx?src=${url}"></iframe>
                  <#--  <iframe class="embed-responsive-item" src="${src}" type="application/pdf"></iframe>  -->
                </div>
            </div>
        </div>
</div>