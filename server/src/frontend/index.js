import React from 'react'
import { render } from 'react-dom'
import { Provider } from 'react-redux'
import thunk from 'redux-thunk'
import { createStore, applyMiddleware } from 'redux'
import rootReducer from './reducers';
import { composeWithDevTools } from 'redux-devtools-extension';
import './index.css'

import ComponentsContainer from './components/App.js'

const middleware = applyMiddleware(thunk);
const store = createStore(
  rootReducer,
  composeWithDevTools(middleware)
);

render(<Provider store={store}><ComponentsContainer/></Provider>,
       document.getElementById('root'))

