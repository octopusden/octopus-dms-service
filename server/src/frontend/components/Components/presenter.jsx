import Header from "../Header";
import {Alert, Spinner} from "@blueprintjs/core";
import Meta from "../Meta";
import ArtifactList from "../ArtifactList";
import Preview from "../Preview";
import Footer from "../Footer";
import React from "react";
import './style.css'
import ComponentGroupPane from "../ComponentGroupPane";
import Search from "./Search";

export default function components(props) {
    const {
        selectedComponent, selectedVersion, hideError, errorMessage,
        confirmation, hideConfirmation, showConfirmation
    } = props
    const versionSelected = !!(selectedComponent && selectedVersion)

    return <div className="components-page-wrapper">
        <Header/>
        <div className="row">
            {errorMessage &&
                <Alert
                    confirmButtonText="Hide"
                    icon="error"
                    intent="danger"
                    isOpen={errorMessage}
                    onConfirm={hideError}
                > {errorMessage} </Alert>
            }
            {confirmation && <Alert
                icon="trash"
                isOpen={confirmation}
                intent="danger"
                cancelButtonText="Cancel"
                confirmButtonText="Ok"
                canEscapeKeyCancel={true}
                canOutsideClickCancel={true}
                onCancel={hideConfirmation}
                onConfirm={() => {
                    confirmation.onConfirm()
                    hideConfirmation()
                }}
            > {confirmation.message} </Alert>
            }
            <div className="left-column">
                <div className='search-block'>
                    <Search/>
                </div>
                <ComponentGroupPane/>
            </div>
            <div className='right-column'>
                {versionSelected && <div className='meta-block'>
                    <Meta/>
                </div>}
                <div className='artifacts-block'>
                    {versionSelected &&
                        <div className='artifacts-list-block'>
                            <ArtifactList showConfirmation={showConfirmation}/>
                        </div>}
                    <div className='preview-block'>
                        <Preview/>
                    </div>
                </div>
            </div>
        </div>
        <Footer/>
    </div>
}